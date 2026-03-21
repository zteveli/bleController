package com.example.blecontroller

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.blecontroller.ble.BleCentralManager
import com.example.blecontroller.data.LayoutRepository
import com.example.blecontroller.data.LayoutWithButtons
import com.example.blecontroller.data.VirtualButtonEntity
import com.example.blecontroller.data.ControlType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class MainViewModel(
    application: Application,
    private val repository: LayoutRepository,
) : AndroidViewModel(application) {

    private companion object {
        const val NEW_BUTTON_WIDTH_FRACTION = 0.26f
        const val NEW_BUTTON_HEIGHT_FRACTION = 0.12f
        const val SLIDER_SEND_MIN_INTERVAL_MS = 20L
        const val FRACTION_EPSILON = 0.0005f
    }

    private val sliderWriteJobs = mutableMapOf<Long, Job>()
    private val sliderPendingValue = mutableMapOf<Long, Float>()
    private val sliderLastSentAt = mutableMapOf<Long, Long>()

    val bleManager = BleCentralManager(application.applicationContext)

    private val selectedLayoutId = MutableStateFlow<Long?>(null)

    val layouts = repository.observeLayouts().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val selectedLayout: StateFlow<LayoutWithButtons?> = combine(layouts, selectedLayoutId) { list, selectedId ->
        when {
            list.isEmpty() -> null
            selectedId == null -> list.first()
            else -> list.firstOrNull { it.layout.id == selectedId } ?: list.first()
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    init {
        viewModelScope.launch {
            repository.ensureDefaultLayout()
        }
    }

    fun selectLayout(id: Long) {
        selectedLayoutId.value = id
    }

    fun createLayout(name: String) {
        viewModelScope.launch {
            selectedLayoutId.value = repository.createLayout(name)
        }
    }

    fun duplicateSelectedLayout(newName: String) {
        val layout = selectedLayout.value ?: return
        viewModelScope.launch {
            selectedLayoutId.value = repository.duplicateLayout(layout, newName)
        }
    }

    fun duplicateLayoutById(layoutId: Long, newName: String) {
        val source = layouts.value.firstOrNull { it.layout.id == layoutId } ?: return
        viewModelScope.launch {
            selectedLayoutId.value = repository.duplicateLayout(source, newName)
        }
    }

    fun deleteSelectedLayout() {
        val current = selectedLayout.value ?: return
        viewModelScope.launch {
            repository.deleteLayout(current.layout)
            selectedLayoutId.value = null
        }
    }

    fun deleteLayoutById(layoutId: Long) {
        val target = layouts.value.firstOrNull { it.layout.id == layoutId } ?: return
        viewModelScope.launch {
            repository.deleteLayout(target.layout)
            if (selectedLayoutId.value == layoutId) {
                selectedLayoutId.value = null
            }
        }
    }

    fun toggleLock() {
        val current = selectedLayout.value ?: return
        viewModelScope.launch {
            repository.updateLayout(
                current.layout.copy(isLocked = !current.layout.isLocked)
            )
        }
    }

    fun bindCurrentDeviceToSelectedLayout() {
        val currentLayout = selectedLayout.value ?: return
        val device = bleManager.connectedDevice.value ?: return
        viewModelScope.launch {
            repository.updateLayout(
                currentLayout.layout.copy(
                    boundDeviceAddress = device.address,
                    boundDeviceName = device.name,
                )
            )
        }
    }


    fun addButtonAt(xFraction: Float, yFraction: Float) {
        addControlAt(xFraction, yFraction, ControlType.BUTTON)
    }

    fun addSliderHorizontalAt(xFraction: Float, yFraction: Float) {
        addControlAt(xFraction, yFraction, ControlType.SLIDER_HORIZONTAL)
    }

    fun addSliderVerticalAt(xFraction: Float, yFraction: Float) {
        addControlAt(xFraction, yFraction, ControlType.SLIDER_VERTICAL)
    }

    private fun addControlAt(xFraction: Float, yFraction: Float, controlType: String) {
        val current = selectedLayout.value ?: return
        val clampedX = xFraction.coerceIn(0f, max(0f, 1f - NEW_BUTTON_WIDTH_FRACTION))
        val clampedY = yFraction.coerceIn(0f, max(0f, 1f - NEW_BUTTON_HEIGHT_FRACTION))
        viewModelScope.launch {
            repository.addControl(
                layoutId = current.layout.id,
                controlType = controlType,
                nextSortOrder = current.buttons.size,
                xFraction = clampedX,
                yFraction = clampedY,
            )
        }
    }

    fun addButton() {
        val current = selectedLayout.value ?: return
        val index = current.buttons.size
        val gridColumns = 3
        val startX = 0.06f
        val startY = 0.08f
        val stepX = 0.30f
        val stepY = 0.16f
        val gridX = startX + (index % gridColumns) * stepX
        val gridY = startY + (index / gridColumns) * stepY
        addButtonAt(gridX, gridY)
    }

    fun addSliderHorizontal() {
        val current = selectedLayout.value ?: return
        val index = current.buttons.size
        val gridColumns = 2
        val startX = 0.06f
        val startY = 0.10f
        val stepX = 0.38f
        val stepY = 0.20f
        val gridX = startX + (index % gridColumns) * stepX
        val gridY = startY + (index / gridColumns) * stepY
        addSliderHorizontalAt(gridX, gridY)
    }

    fun addSliderVertical() {
        val current = selectedLayout.value ?: return
        val index = current.buttons.size
        val gridColumns = 3
        val startX = 0.06f
        val startY = 0.10f
        val stepX = 0.22f
        val stepY = 0.26f
        val gridX = startX + (index % gridColumns) * stepX
        val gridY = startY + (index / gridColumns) * stepY
        addSliderVerticalAt(gridX, gridY)
    }

    fun updateButtonConfig(
        buttonId: Long,
        label: String,
        serviceUuid: String,
        characteristicUuid: String,
        payloadHex: String,
    ) {
        val button = selectedLayout.value?.buttons?.firstOrNull { it.id == buttonId } ?: return
        viewModelScope.launch {
            repository.updateButton(
                button.copy(
                    label = label.ifBlank { button.label },
                    serviceUuid = serviceUuid,
                    characteristicUuid = characteristicUuid,
                    payloadHex = payloadHex,
                )
            )
        }
    }

    fun updateSliderConfig(
        buttonId: Long,
        label: String,
        serviceUuid: String,
        characteristicUuid: String,
        sliderMin: Float,
        sliderMax: Float,
        sliderPrefix: String,
    ) {
        val button = selectedLayout.value?.buttons?.firstOrNull { it.id == buttonId } ?: return
        val min = kotlin.math.min(sliderMin, sliderMax)
        val maxRange = kotlin.math.max(sliderMin, sliderMax)
        val clampedValue = button.sliderValue.coerceIn(min, maxRange)
        viewModelScope.launch {
            repository.updateButton(
                button.copy(
                    label = label.ifBlank { button.label },
                    serviceUuid = serviceUuid,
                    characteristicUuid = characteristicUuid,
                    sliderMin = min,
                    sliderMax = maxRange,
                    sliderPrefix = sliderPrefix,
                    sliderValue = clampedValue,
                )
            )
        }
    }

    fun updateSliderValue(buttonId: Long, sliderValue: Float) {
        val button = selectedLayout.value?.buttons?.firstOrNull { it.id == buttonId } ?: return
        val clamped = sliderValue.coerceIn(button.sliderMin, button.sliderMax)
        viewModelScope.launch {
            repository.updateButton(button.copy(sliderValue = clamped))
        }
    }

    fun triggerSliderWrite(buttonId: Long, sliderValue: Float) {
        val button = selectedLayout.value?.buttons?.firstOrNull { it.id == buttonId } ?: return
        if (button.serviceUuid.isBlank() || button.characteristicUuid.isBlank()) return

        sliderPendingValue[buttonId] = sliderValue.coerceIn(button.sliderMin, button.sliderMax)
        if (sliderWriteJobs[buttonId]?.isActive == true) return

        sliderWriteJobs[buttonId] = viewModelScope.launch {
            while (true) {
                val pending = sliderPendingValue[buttonId] ?: break
                val now = SystemClock.elapsedRealtime()
                val elapsed = now - (sliderLastSentAt[buttonId] ?: 0L)
                if (elapsed < SLIDER_SEND_MIN_INTERVAL_MS) {
                    delay(SLIDER_SEND_MIN_INTERVAL_MS - elapsed)
                }

                val latest = sliderPendingValue.remove(buttonId) ?: continue
                val payload = "${button.sliderPrefix}${latest.roundToInt()}".toByteArray(Charsets.UTF_8)
                bleManager.writeCharacteristic(button.serviceUuid, button.characteristicUuid, payload)
                sliderLastSentAt[buttonId] = SystemClock.elapsedRealtime()

                if (!sliderPendingValue.containsKey(buttonId)) {
                    break
                }
            }
            sliderWriteJobs.remove(buttonId)
        }
    }

    fun updateButtonPosition(buttonId: Long, xFraction: Float, yFraction: Float) {
        val button = selectedLayout.value?.buttons?.firstOrNull { it.id == buttonId } ?: return
        val newX = xFraction.coerceIn(0f, max(0f, 1f - button.widthFraction))
        val newY = yFraction.coerceIn(0f, max(0f, 1f - button.heightFraction))
        if (abs(button.xFraction - newX) < FRACTION_EPSILON && abs(button.yFraction - newY) < FRACTION_EPSILON) {
            return
        }
        viewModelScope.launch {
            repository.updateButton(
                button.copy(
                    xFraction = newX,
                    yFraction = newY,
                )
            )
        }
    }

    fun updateButtonSize(buttonId: Long, widthFraction: Float, heightFraction: Float) {
        val button = selectedLayout.value?.buttons?.firstOrNull { it.id == buttonId } ?: return
        val isSlider = button.controlType != ControlType.BUTTON
        val maxWidth = if (isSlider) 0.98f else 0.8f
        val maxHeight = if (isSlider) 0.98f else 0.45f
        val minWidth = if (isSlider) 0.10f else 0.12f
        val minHeight = if (isSlider) 0.06f else 0.08f
        val newWidth = widthFraction.coerceIn(minWidth, maxWidth)
        val newHeight = heightFraction.coerceIn(minHeight, maxHeight)
        val newX = button.xFraction.coerceIn(0f, 1f - newWidth)
        val newY = button.yFraction.coerceIn(0f, 1f - newHeight)
        if (
            abs(button.widthFraction - newWidth) < FRACTION_EPSILON &&
            abs(button.heightFraction - newHeight) < FRACTION_EPSILON &&
            abs(button.xFraction - newX) < FRACTION_EPSILON &&
            abs(button.yFraction - newY) < FRACTION_EPSILON
        ) {
            return
        }
        viewModelScope.launch {
            repository.updateButton(
                button.copy(
                    widthFraction = newWidth,
                    heightFraction = newHeight,
                    xFraction = newX,
                    yFraction = newY,
                )
            )
        }
    }

    fun deleteButton(buttonId: Long) {
        val button = selectedLayout.value?.buttons?.firstOrNull { it.id == buttonId } ?: return
        viewModelScope.launch {
            repository.deleteButton(button)
        }
    }

    fun triggerButtonWrite(button: VirtualButtonEntity): Boolean {
        if (button.controlType != ControlType.BUTTON) return false
        val payload = button.payloadHex.hexToBytesOrNull() ?: return false
        if (button.serviceUuid.isBlank() || button.characteristicUuid.isBlank()) return false
        return bleManager.writeCharacteristic(button.serviceUuid, button.characteristicUuid, payload)
    }

    private fun String.hexToBytesOrNull(): ByteArray? {
        val cleaned = replace(" ", "").replace("-", "")
        if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null
        return runCatching {
            cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }.getOrNull()
    }

    class Factory(
        private val application: Application,
        private val repository: LayoutRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(application, repository) as T
        }
    }
}
