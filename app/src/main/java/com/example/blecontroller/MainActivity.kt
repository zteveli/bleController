package com.example.blecontroller

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.example.blecontroller.ble.GattCharacteristicUi
import com.example.blecontroller.ble.GattServiceUi
import com.example.blecontroller.data.BleControllerDatabase
import com.example.blecontroller.data.ControlType
import com.example.blecontroller.data.LayoutRepository
import com.example.blecontroller.data.LayoutWithButtons
import com.example.blecontroller.data.VirtualButtonEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class PayloadInputMode {
    HEX,
    TEXT,
}

private data class ControllerUiState(
    val showCreateDialog: Boolean = false,
    val showDuplicateDialog: Boolean = false,
    val duplicateTargetLayoutId: Long? = null,
    val deleteTargetLayoutId: Long? = null,
    val editingButtonId: Long? = null,
    val layoutMenuExpanded: Boolean = false,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = BleControllerDatabase.getInstance(applicationContext)
        val repository = LayoutRepository(database.layoutDao())
        val factory = MainViewModel.Factory(application, repository)

        setContent {
            MaterialTheme {
                val vm: MainViewModel = viewModel(factory = factory)
                BleControllerApp(vm = vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BleControllerApp(vm: MainViewModel) {
    val tabs = listOf("BLE", "Controller")
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val statusText by vm.bleManager.statusText.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    var settingsExpanded by remember { mutableStateOf(false) }
    var hasRequestedPermissions by rememberSaveable { mutableStateOf(false) }

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.all { it }
        scope.launch {
            if (granted) {
                snackbarHostState.showSnackbar("BLE permissions granted")
            } else {
                snackbarHostState.showSnackbar("BLE permissions are required. Closing app...")
                delay(1200)
                activity?.finishAffinity()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasAllPermissions(context, permissions) && !hasRequestedPermissions) {
            hasRequestedPermissions = true
            scope.launch { snackbarHostState.showSnackbar("Requesting BLE permissions") }
            permissionLauncher.launch(permissions)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BleController") },
                actions = {
                    Icon(imageVector = Icons.Default.Bluetooth, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box {
                        IconButton(onClick = { settingsExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                        }
                        DropdownMenu(expanded = settingsExpanded, onDismissRequest = { settingsExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Close app") },
                                onClick = {
                                    settingsExpanded = false
                                    activity?.finishAffinity()
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            when (selectedTab) {
                0 -> BleScreen(
                    vm = vm,
                    showMessage = { message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    },
                )

                1 -> ControllerScreen(
                    vm = vm,
                    showMessage = { message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    },
                )
            }
        }
    }
}

@Composable
private fun BleScreen(
    vm: MainViewModel,
    showMessage: (String) -> Unit,
) {
    val scanResults by vm.bleManager.scanResults.collectAsState()
    val services by vm.bleManager.gattServices.collectAsState()
    val connectedDevice by vm.bleManager.connectedDevice.collectAsState()
    val isScanning by vm.bleManager.isScanning.collectAsState()
    val selectedLayout by vm.selectedLayout.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("BLE controls", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { if (isScanning) vm.bleManager.stopScan() else vm.bleManager.startScan() }) {
                            Text(if (isScanning) "Stop scan" else "Start scan")
                        }
                    }
                    if (!vm.bleManager.isBluetoothAvailable()) {
                        Text("This device does not support BLE or has no Bluetooth adapter.")
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Active connection", style = MaterialTheme.typography.titleMedium)
                    if (connectedDevice == null) {
                        Text("No BLE device connected.")
                    } else {
                        Text("Device: ${connectedDevice?.name ?: "Unknown"}")
                        Text("Address: ${connectedDevice?.address}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.bleManager.disconnect() }) {
                                Text("Disconnect")
                            }
                            Button(
                                onClick = {
                                    vm.bindCurrentDeviceToSelectedLayout()
                                    showMessage("Current device bound to selected layout")
                                },
                                enabled = selectedLayout != null,
                            ) {
                                Text("Bind to layout")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Discovered devices", style = MaterialTheme.typography.titleMedium)
        }

        items(scanResults, key = { it.address }) { device ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(device.name ?: "Unnamed peripheral", fontWeight = FontWeight.SemiBold)
                        Text(device.address, style = MaterialTheme.typography.bodySmall)
                        Text("RSSI: ${device.rssi}", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { vm.bleManager.connect(device.address) }) {
                        Text("Connect")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Discovered GATT services", style = MaterialTheme.typography.titleMedium)
        }

        items(services, key = { it.uuid }) { service ->
            ServiceCard(service)
        }
    }
}

@Composable
private fun ServiceCard(service: GattServiceUi) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Service", fontWeight = FontWeight.Bold)
            Text(service.uuid, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            if (service.characteristics.isEmpty()) {
                Text("No characteristics")
            } else {
                service.characteristics.forEach { ch ->
                    Column {
                        Text(ch.characteristicUuid, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (ch.canWrite) "Writable characteristic" else "Read only / notify",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ch.canWrite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ControllerScreen(
    vm: MainViewModel,
    showMessage: (String) -> Unit,
) {
    val layouts by vm.layouts.collectAsState()
    val selectedLayout by vm.selectedLayout.collectAsState()
    val services by vm.bleManager.gattServices.collectAsState()

    var uiState by remember { mutableStateOf(ControllerUiState()) }

    val editingButton = selectedLayout?.buttons?.firstOrNull { it.id == uiState.editingButtonId }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val controllerViewportHeight = maxHeight

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LayoutsCard(
            layouts = layouts,
            selectedLayout = selectedLayout,
            layoutMenuExpanded = uiState.layoutMenuExpanded,
            onLayoutMenuExpandedChange = { expanded -> uiState = uiState.copy(layoutMenuExpanded = expanded) },
            onSelectLayout = vm::selectLayout,
            onDuplicateLayoutRequest = { layoutId ->
                uiState = uiState.copy(
                    duplicateTargetLayoutId = layoutId,
                    showDuplicateDialog = true,
                )
            },
            onDeleteLayoutRequest = { layoutId ->
                uiState = uiState.copy(deleteTargetLayoutId = layoutId)
            },
            onCreateLayoutRequest = { uiState = uiState.copy(showCreateDialog = true) },
        )

        selectedLayout?.let { layout ->
            ControllerCanvas(
                modifier = Modifier
                    .fillMaxWidth(),
                whiteCanvasHeight = controllerViewportHeight,
                layout = layout,
                services = services,
                onEditButton = { buttonId -> uiState = uiState.copy(editingButtonId = buttonId) },
                onAddButton = vm::addButton,
                onAddHorizontalSlider = vm::addSliderHorizontal,
                onAddVerticalSlider = vm::addSliderVertical,
                onToggleLock = vm::toggleLock,
                onMoveButton = vm::updateButtonPosition,
                onResizeButton = vm::updateButtonSize,
                onSliderValueChanged = vm::triggerSliderWrite,
                onSliderValueCommitted = vm::updateSliderValue,
                onTriggerWrite = { button ->
                    if (!vm.triggerButtonWrite(button)) {
                        showMessage("Write failed. Check button BLE mapping and payload.")
                    }
                },
            )
        } ?: run {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No layout selected.",
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    } // Column
    } // BoxWithConstraints

    if (uiState.showCreateDialog) {
        NameInputDialog(
            title = "New layout",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { uiState = uiState.copy(showCreateDialog = false) },
            onConfirm = {
                vm.createLayout(it)
                uiState = uiState.copy(showCreateDialog = false)
            },
        )
    }

    val duplicateTargetLayout = layouts.firstOrNull { it.layout.id == uiState.duplicateTargetLayoutId }
    DuplicateLayoutDialog(
        targetLayout = duplicateTargetLayout,
        show = uiState.showDuplicateDialog,
        onDismiss = {
            uiState = uiState.copy(
                showDuplicateDialog = false,
                duplicateTargetLayoutId = null,
            )
        },
        onConfirm = { layoutId, newName ->
            vm.duplicateLayoutById(layoutId, newName)
            uiState = uiState.copy(
                showDuplicateDialog = false,
                duplicateTargetLayoutId = null,
            )
        },
    )

    val deleteTargetLayout = layouts.firstOrNull { it.layout.id == uiState.deleteTargetLayoutId }
    DeleteLayoutDialog(
        targetLayout = deleteTargetLayout,
        canDelete = layouts.size > 1,
        onDismiss = { uiState = uiState.copy(deleteTargetLayoutId = null) },
        onConfirm = { layoutId ->
            vm.deleteLayoutById(layoutId)
            uiState = uiState.copy(deleteTargetLayoutId = null)
        },
    )

    if (editingButton != null) {
        ButtonEditDialog(
            button = editingButton,
            services = services,
            onDismiss = { uiState = uiState.copy(editingButtonId = null) },
            onSave = { label, serviceUuid, characteristicUuid, payloadHex, sliderMin, sliderMax, sliderPrefix ->
                if (editingButton.controlType == ControlType.BUTTON) {
                    vm.updateButtonConfig(editingButton.id, label, serviceUuid, characteristicUuid, payloadHex)
                } else {
                    vm.updateSliderConfig(
                        buttonId = editingButton.id,
                        label = label,
                        serviceUuid = serviceUuid,
                        characteristicUuid = characteristicUuid,
                        sliderMin = sliderMin,
                        sliderMax = sliderMax,
                        sliderPrefix = sliderPrefix,
                    )
                }
                uiState = uiState.copy(editingButtonId = null)
            },
            onDelete = {
                vm.deleteButton(editingButton.id)
                uiState = uiState.copy(editingButtonId = null)
            },
        )
    }
}

@Composable
private fun LayoutsCard(
    layouts: List<LayoutWithButtons>,
    selectedLayout: LayoutWithButtons?,
    layoutMenuExpanded: Boolean,
    onLayoutMenuExpandedChange: (Boolean) -> Unit,
    onSelectLayout: (Long) -> Unit,
    onDuplicateLayoutRequest: (Long) -> Unit,
    onDeleteLayoutRequest: (Long) -> Unit,
    onCreateLayoutRequest: () -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Layouts", style = MaterialTheme.typography.titleMedium)
            Box {
                OutlinedButton(onClick = { onLayoutMenuExpandedChange(true) }) {
                    Text(selectedLayout?.layout?.name ?: "Select layout")
                }
                DropdownMenu(
                    expanded = layoutMenuExpanded,
                    onDismissRequest = { onLayoutMenuExpandedChange(false) },
                ) {
                    layouts.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.layout.name) },
                            onClick = {
                                onSelectLayout(item.layout.id)
                                onLayoutMenuExpandedChange(false)
                            },
                            trailingIcon = {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    IconButton(
                                        onClick = {
                                            onDuplicateLayoutRequest(item.layout.id)
                                            onLayoutMenuExpandedChange(false)
                                        },
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate layout")
                                    }
                                    IconButton(
                                        enabled = layouts.size > 1,
                                        onClick = {
                                            onDeleteLayoutRequest(item.layout.id)
                                            onLayoutMenuExpandedChange(false)
                                        },
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete layout")
                                    }
                                }
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("+ New layout") },
                        onClick = {
                            onLayoutMenuExpandedChange(false)
                            onCreateLayoutRequest()
                        },
                    )
                }
            }

            selectedLayout?.let { layout ->
                Text(
                    "Bound BLE device: ${layout.layout.boundDeviceName ?: "—"} (${layout.layout.boundDeviceAddress ?: "—"})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DuplicateLayoutDialog(
    targetLayout: LayoutWithButtons?,
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (layoutId: Long, newName: String) -> Unit,
) {
    if (!show || targetLayout == null) return

    NameInputDialog(
        title = "Duplicate layout",
        initial = "${targetLayout.layout.name} copy",
        confirmLabel = "Save",
        onDismiss = onDismiss,
        onConfirm = { onConfirm(targetLayout.layout.id, it) },
    )
}

@Composable
private fun DeleteLayoutDialog(
    targetLayout: LayoutWithButtons?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (layoutId: Long) -> Unit,
) {
    if (targetLayout == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete layout") },
        text = { Text("Are you sure you want to delete '${targetLayout.layout.name}'?") },
        confirmButton = {
            Button(
                onClick = { onConfirm(targetLayout.layout.id) },
                enabled = canDelete,
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ControllerCanvas(
    modifier: Modifier = Modifier,
    whiteCanvasHeight: Dp,
    layout: LayoutWithButtons,
    services: List<GattServiceUi>,
    onEditButton: (Long) -> Unit,
    onAddButton: () -> Unit,
    onAddHorizontalSlider: () -> Unit,
    onAddVerticalSlider: () -> Unit,
    onToggleLock: () -> Unit,
    onMoveButton: (Long, Float, Float) -> Unit,
    onResizeButton: (Long, Float, Float) -> Unit,
    onSliderValueChanged: (Long, Float) -> Unit,
    onSliderValueCommitted: (Long, Float) -> Unit,
    onTriggerWrite: (VirtualButtonEntity) -> Unit,
) {
    val locked = layout.layout.isLocked
    val modeColor = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    var addMenuExpanded by remember(layout.layout.id) { mutableStateOf(false) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Controller canvas", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onToggleLock) {
                    Icon(
                        imageVector = if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (locked) "Switch to edit mode" else "Switch to live mode",
                        tint = modeColor,
                    )
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(whiteCanvasHeight)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
            ) {
                val canvasWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
                val canvasHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

                layout.buttons.forEach { button ->
                    val supportsWrite = services.any { service ->
                        service.uuid.equals(button.serviceUuid, ignoreCase = true) &&
                            service.characteristics.any {
                                it.characteristicUuid.equals(button.characteristicUuid, ignoreCase = true) && it.canWrite
                            }
                    }

                    ControllerButton(
                        button = button,
                        canvasWidthPx = canvasWidthPx,
                        canvasHeightPx = canvasHeightPx,
                        editMode = !locked,
                        supportsWrite = supportsWrite,
                        onEdit = { onEditButton(button.id) },
                        onMoved = { x, y -> onMoveButton(button.id, x, y) },
                        onResized = { w, h -> onResizeButton(button.id, w, h) },
                        onSliderValueChanged = onSliderValueChanged,
                        onSliderValueCommitted = onSliderValueCommitted,
                        onTrigger = { onTriggerWrite(button) },
                    )
                }

                if (!locked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    ) {
                        FloatingActionButton(
                            onClick = { addMenuExpanded = true },
                            containerColor = Color(0xFF1E88E5),
                            contentColor = Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add control")
                        }
                        DropdownMenu(
                            expanded = addMenuExpanded,
                            onDismissRequest = { addMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add button") },
                                onClick = {
                                    addMenuExpanded = false
                                    onAddButton()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Add horizontal slider") },
                                onClick = {
                                    addMenuExpanded = false
                                    onAddHorizontalSlider()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Add vertical slider") },
                                onClick = {
                                    addMenuExpanded = false
                                    onAddVerticalSlider()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControllerButton(
    button: VirtualButtonEntity,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    editMode: Boolean,
    supportsWrite: Boolean,
    onEdit: () -> Unit,
    onMoved: (Float, Float) -> Unit,
    onResized: (Float, Float) -> Unit,
    onSliderValueChanged: (Long, Float) -> Unit,
    onSliderValueCommitted: (Long, Float) -> Unit,
    onTrigger: () -> Unit,
) {
    var localX by remember(button.id, button.xFraction) { mutableStateOf(button.xFraction * canvasWidthPx) }
    var localY by remember(button.id, button.yFraction) { mutableStateOf(button.yFraction * canvasHeightPx) }
    var localWidth by remember(button.id, button.widthFraction) { mutableStateOf(button.widthFraction * canvasWidthPx) }
    var localHeight by remember(button.id, button.heightFraction) { mutableStateOf(button.heightFraction * canvasHeightPx) }
    var localSliderValue by remember(button.id, button.sliderValue) { mutableStateOf(button.sliderValue) }

    LaunchedEffect(button.xFraction, button.yFraction, button.widthFraction, button.heightFraction, canvasWidthPx, canvasHeightPx) {
        localX = button.xFraction * canvasWidthPx
        localY = button.yFraction * canvasHeightPx
        localWidth = button.widthFraction * canvasWidthPx
        localHeight = button.heightFraction * canvasHeightPx
    }

    LaunchedEffect(button.sliderValue) {
        localSliderValue = button.sliderValue
    }

    Box(
        modifier = Modifier
            .absoluteOffset { IntOffset(localX.roundToInt(), localY.roundToInt()) }
            .size(
                width = with(LocalDensity.current) { localWidth.toDp() },
                height = with(LocalDensity.current) { localHeight.toDp() },
            )
            .clip(RoundedCornerShape(18.dp))
            .background(
                when {
                    editMode -> MaterialTheme.colorScheme.secondaryContainer
                    supportsWrite -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer
                }
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = {
                    if (button.controlType == ControlType.BUTTON) {
                        if (editMode) onEdit() else onTrigger()
                    } else if (editMode) {
                        onEdit()
                    }
                },
                onLongClick = { onEdit() },
            )
            .pointerInput(editMode, canvasWidthPx, canvasHeightPx, localWidth, localHeight) {
                if (!editMode) return@pointerInput
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        localX = (localX + dragAmount.x).coerceIn(0f, canvasWidthPx - localWidth)
                        localY = (localY + dragAmount.y).coerceIn(0f, canvasHeightPx - localHeight)
                    },
                    onDragEnd = {
                        onMoved(localX / canvasWidthPx, localY / canvasHeightPx)
                    },
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = button.label, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (button.controlType == ControlType.BUTTON) {
                Text(
                    text = if (button.serviceUuid.isBlank()) "No BLE binding" else button.payloadHex,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                if (button.controlType == ControlType.SLIDER_VERTICAL) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Slider(
                            value = localSliderValue.coerceIn(button.sliderMin, button.sliderMax),
                            onValueChange = { newValue ->
                                localSliderValue = newValue
                                if (!editMode) onSliderValueChanged(button.id, newValue)
                            },
                            onValueChangeFinished = {
                                if (!editMode) onSliderValueCommitted(button.id, localSliderValue)
                            },
                            valueRange = button.sliderMin..button.sliderMax,
                            enabled = !editMode,
                            modifier = Modifier.layout { measurable, constraints ->
                                // Swap width↔height so the slider track length = container height
                                val placeable = measurable.measure(
                                    androidx.compose.ui.unit.Constraints(
                                        minWidth = constraints.minHeight,
                                        maxWidth = constraints.maxHeight,
                                        minHeight = constraints.minWidth,
                                        maxHeight = constraints.maxWidth,
                                    )
                                )
                                layout(placeable.height, placeable.width) {
                                    placeable.placeWithLayer(
                                        x = -(placeable.width - placeable.height) / 2,
                                        y = -(placeable.height - placeable.width) / 2,
                                    ) {
                                        rotationZ = -90f
                                    }
                                }
                            },
                        )
                    }
                } else {
                    Slider(
                        value = localSliderValue.coerceIn(button.sliderMin, button.sliderMax),
                        onValueChange = { newValue ->
                            localSliderValue = newValue
                            if (!editMode) onSliderValueChanged(button.id, newValue)
                        },
                        onValueChangeFinished = {
                            if (!editMode) onSliderValueCommitted(button.id, localSliderValue)
                        },
                        valueRange = button.sliderMin..button.sliderMax,
                        enabled = !editMode,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        if (editMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .pointerInput(canvasWidthPx, canvasHeightPx, button.controlType) {
                        val isSlider = button.controlType != ControlType.BUTTON
                        val minW = if (isSlider) 40f else 80f
                        val minH = if (isSlider) 30f else 52f
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                localWidth = (localWidth + dragAmount.x).coerceIn(minW, canvasWidthPx - localX)
                                localHeight = (localHeight + dragAmount.y).coerceIn(minH, canvasHeightPx - localY)
                            },
                            onDragEnd = {
                                onResized(localWidth / canvasWidthPx, localHeight / canvasHeightPx)
                            },
                        )
                    },
            )
        }
    }
}

@Composable
private fun NameInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text.ifBlank { title }) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ButtonEditDialog(
    button: VirtualButtonEntity,
    services: List<GattServiceUi>,
    onDismiss: () -> Unit,
    onSave: (
        label: String,
        serviceUuid: String,
        characteristicUuid: String,
        payloadHex: String,
        sliderMin: Float,
        sliderMax: Float,
        sliderPrefix: String,
    ) -> Unit,
    onDelete: () -> Unit,
) {
    var label by remember(button.id) { mutableStateOf(button.label) }
    var payloadHex by remember(button.id) { mutableStateOf(button.payloadHex) }
    var payloadText by remember(button.id) { mutableStateOf(button.payloadHex.hexToReadableUtf8OrNull().orEmpty()) }
    var payloadMode by remember(button.id) { mutableStateOf(PayloadInputMode.HEX) }
    var sliderMinText by remember(button.id) { mutableStateOf(button.sliderMin.toString()) }
    var sliderMaxText by remember(button.id) { mutableStateOf(button.sliderMax.toString()) }
    var sliderPrefix by remember(button.id) { mutableStateOf(button.sliderPrefix) }
    var selectedServiceUuid by remember(button.id) { mutableStateOf(button.serviceUuid) }
    var selectedCharacteristicUuid by remember(button.id) { mutableStateOf(button.characteristicUuid) }

    val writableCharacteristics: List<GattCharacteristicUi> = services.flatMap { service ->
        service.characteristics.filter { it.canWrite }.map { it.copy(serviceUuid = service.uuid) }
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(16.dp),
        onDismissRequest = onDismiss,
        title = { Text("Edit virtual button") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Button label") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (button.controlType == ControlType.BUTTON) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = payloadMode == PayloadInputMode.HEX,
                            onClick = {
                                payloadHex = payloadText.utf8ToHex()
                                payloadMode = PayloadInputMode.HEX
                            },
                            label = { Text("HEX") },
                        )
                        FilterChip(
                            selected = payloadMode == PayloadInputMode.TEXT,
                            onClick = {
                                payloadText = payloadHex.hexToReadableUtf8OrNull().orEmpty()
                                payloadMode = PayloadInputMode.TEXT
                            },
                            label = { Text("Text") },
                        )
                    }
                    OutlinedTextField(
                        value = if (payloadMode == PayloadInputMode.HEX) payloadHex else payloadText,
                        onValueChange = {
                            if (payloadMode == PayloadInputMode.HEX) {
                                payloadHex = it.uppercase()
                            } else {
                                payloadText = it
                            }
                        },
                        label = {
                            Text(
                                if (payloadMode == PayloadInputMode.HEX) {
                                    "HEX payload (e.g. 01 or A0FF)"
                                } else {
                                    "Text payload"
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = sliderMinText,
                        onValueChange = { sliderMinText = it },
                        label = { Text("Minimum value") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = sliderMaxText,
                        onValueChange = { sliderMaxText = it },
                        label = { Text("Maximum value") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = sliderPrefix,
                        onValueChange = { sliderPrefix = it },
                        label = { Text("Value prefix") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                Text("Select writable characteristic", fontWeight = FontWeight.SemiBold)
                if (writableCharacteristics.isEmpty()) {
                    Text("No writable characteristic found. Connect to a BLE device first.")
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        writableCharacteristics.forEach { characteristic ->
                            FilterChip(
                                selected = selectedServiceUuid.equals(characteristic.serviceUuid, true) &&
                                    selectedCharacteristicUuid.equals(characteristic.characteristicUuid, true),
                                onClick = {
                                    selectedServiceUuid = characteristic.serviceUuid
                                    selectedCharacteristicUuid = characteristic.characteristicUuid
                                },
                                label = {
                                    Text(
                                        characteristic.characteristicUuid.take(8),
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Selected service UUID:")
                    AssistChip(onClick = {}, label = { Text(selectedServiceUuid.ifBlank { "—" }) })
                    Text("Selected characteristic UUID:")
                    AssistChip(onClick = {}, label = { Text(selectedCharacteristicUuid.ifBlank { "—" }) })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val payloadForSave = if (payloadMode == PayloadInputMode.HEX) {
                        payloadHex
                    } else {
                        payloadText.utf8ToHex()
                    }
                    onSave(
                        label,
                        selectedServiceUuid,
                        selectedCharacteristicUuid,
                        payloadForSave,
                        sliderMinText.toFloatOrNull() ?: button.sliderMin,
                        sliderMaxText.toFloatOrNull() ?: button.sliderMax,
                        sliderPrefix,
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

private fun String.utf8ToHex(): String {
    return toByteArray(Charsets.UTF_8).joinToString(separator = "") { eachByte ->
        "%02X".format(eachByte.toInt() and 0xFF)
    }
}

private fun String.hexToReadableUtf8OrNull(): String? {
    val cleaned = replace(" ", "").replace("-", "")
    if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null

    val bytes = runCatching {
        cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }.getOrNull() ?: return null

    val decoded = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull() ?: return null
    return if (decoded.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) {
        null
    } else {
        decoded
    }
}

private fun hasAllPermissions(context: Context, permissions: Array<String>): Boolean {
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

