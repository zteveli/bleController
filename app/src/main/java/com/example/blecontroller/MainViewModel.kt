package com.example.blecontroller

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.blecontroller.ble.BleCentralManager
import com.example.blecontroller.data.LayoutRepository
import com.example.blecontroller.data.LayoutWithButtons
import com.example.blecontroller.data.VirtualButtonEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max

class MainViewModel(
    application: Application,
    private val repository: LayoutRepository,
) : AndroidViewModel(application) {

    private companion object {
        const val NEW_BUTTON_WIDTH_FRACTION = 0.26f
        const val NEW_BUTTON_HEIGHT_FRACTION = 0.12f
    }

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

    fun deleteSelectedLayout() {
        val current = selectedLayout.value ?: return
        viewModelScope.launch {
            repository.deleteLayout(current.layout)
            selectedLayoutId.value = null
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
        val current = selectedLayout.value ?: return
        val clampedX = xFraction.coerceIn(0f, max(0f, 1f - NEW_BUTTON_WIDTH_FRACTION))
        val clampedY = yFraction.coerceIn(0f, max(0f, 1f - NEW_BUTTON_HEIGHT_FRACTION))
        viewModelScope.launch {
            repository.addButton(
                layoutId = current.layout.id,
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

    fun updateButtonPosition(buttonId: Long, xFraction: Float, yFraction: Float) {
        val button = selectedLayout.value?.buttons?.firstOrNull { it.id == buttonId } ?: return
        viewModelScope.launch {
            repository.updateButton(
                button.copy(
                    xFraction = xFraction.coerceIn(0f, max(0f, 1f - button.widthFraction)),
                    yFraction = yFraction.coerceIn(0f, max(0f, 1f - button.heightFraction)),
                )
            )
        }
    }

    fun updateButtonSize(buttonId: Long, widthFraction: Float, heightFraction: Float) {
        val button = selectedLayout.value?.buttons?.firstOrNull { it.id == buttonId } ?: return
        val newWidth = widthFraction.coerceIn(0.12f, 0.8f)
        val newHeight = heightFraction.coerceIn(0.08f, 0.45f)
        viewModelScope.launch {
            repository.updateButton(
                button.copy(
                    widthFraction = newWidth,
                    heightFraction = newHeight,
                    xFraction = button.xFraction.coerceIn(0f, 1f - newWidth),
                    yFraction = button.yFraction.coerceIn(0f, 1f - newHeight),
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
