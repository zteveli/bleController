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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.example.blecontroller.ble.GattCharacteristicUi
import com.example.blecontroller.ble.GattServiceUi
import com.example.blecontroller.data.BleControllerDatabase
import com.example.blecontroller.data.LayoutRepository
import com.example.blecontroller.data.LayoutWithButtons
import com.example.blecontroller.data.VirtualButtonEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
            TabRow(selectedTabIndex = selectedTab) {
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
                    Text("BLE permissions and controls", style = MaterialTheme.typography.titleMedium)
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
    val connectedDevice by vm.bleManager.connectedDevice.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var editingButtonId by remember { mutableStateOf<Long?>(null) }
    var layoutMenuExpanded by remember { mutableStateOf(false) }
    val isLayoutLocked = selectedLayout?.layout?.isLocked == true

    val editingButton = selectedLayout?.buttons?.firstOrNull { it.id == editingButtonId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Layouts", style = MaterialTheme.typography.titleMedium)
                Box {
                    OutlinedButton(onClick = { layoutMenuExpanded = true }) {
                        Text(selectedLayout?.layout?.name ?: "Select layout")
                    }
                    DropdownMenu(expanded = layoutMenuExpanded, onDismissRequest = { layoutMenuExpanded = false }) {
                        layouts.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.layout.name) },
                                onClick = {
                                    vm.selectLayout(item.layout.id)
                                    layoutMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { vm.addButton() },
                        enabled = selectedLayout != null && !isLayoutLocked,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add button")
                    }
                    OutlinedButton(onClick = { showCreateDialog = true }) { Text("New layout") }
                    IconButton(onClick = { showDuplicateDialog = true }, enabled = selectedLayout != null) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate")
                    }
                    IconButton(onClick = { vm.deleteSelectedLayout() }, enabled = selectedLayout != null && layouts.size > 1) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                    IconButton(onClick = { vm.toggleLock() }, enabled = selectedLayout != null) {
                        Icon(
                            if (selectedLayout?.layout?.isLocked == true) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Lock",
                        )
                    }
                    IconButton(onClick = { vm.bindCurrentDeviceToSelectedLayout() }, enabled = selectedLayout != null && connectedDevice != null) {
                        Icon(Icons.Default.Save, contentDescription = "Save BLE binding")
                    }
                }

                selectedLayout?.let { layout ->
                    Text("Layout locked: ${if (layout.layout.isLocked) "yes" else "no"}")
                    Text(
                        "Bound BLE device: ${layout.layout.boundDeviceName ?: "—"} (${layout.layout.boundDeviceAddress ?: "—"})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        selectedLayout?.let { layout ->
            ControllerCanvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                layout = layout,
                services = services,
                onEditButton = { editingButtonId = it },
                onAddButton = vm::addButton,
                onMoveButton = vm::updateButtonPosition,
                onResizeButton = vm::updateButtonSize,
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
    }

    if (showCreateDialog) {
        NameInputDialog(
            title = "New layout",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onConfirm = {
                vm.createLayout(it)
                showCreateDialog = false
            },
        )
    }

    val currentSelectedLayout = selectedLayout
    if (showDuplicateDialog && currentSelectedLayout != null) {
        NameInputDialog(
            title = "Duplicate layout",
            initial = "${currentSelectedLayout.layout.name} copy",
            confirmLabel = "Save",
            onDismiss = { showDuplicateDialog = false },
            onConfirm = {
                vm.duplicateSelectedLayout(it)
                showDuplicateDialog = false
            },
        )
    }

    if (editingButton != null) {
        ButtonEditDialog(
            button = editingButton,
            services = services,
            onDismiss = { editingButtonId = null },
            onSave = { label, serviceUuid, characteristicUuid, payloadHex ->
                vm.updateButtonConfig(editingButton.id, label, serviceUuid, characteristicUuid, payloadHex)
                editingButtonId = null
            },
            onDelete = {
                vm.deleteButton(editingButton.id)
                editingButtonId = null
            },
        )
    }
}

@Composable
private fun ControllerCanvas(
    modifier: Modifier = Modifier,
    layout: LayoutWithButtons,
    services: List<GattServiceUi>,
    onEditButton: (Long) -> Unit,
    onAddButton: () -> Unit,
    onMoveButton: (Long, Float, Float) -> Unit,
    onResizeButton: (Long, Float, Float) -> Unit,
    onTriggerWrite: (VirtualButtonEntity) -> Unit,
) {
    val locked = layout.layout.isLocked

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
                Text(
                    if (locked) "Live mode" else "Edit mode",
                    color = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                "In edit mode buttons can be moved and resized. In live mode tapping sends a BLE characteristic write.",
                style = MaterialTheme.typography.bodySmall,
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                        onTrigger = { onTriggerWrite(button) },
                    )
                }

                FloatingActionButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    onClick = { if (!locked) onAddButton() },
                    containerColor = Color(0xFF1E88E5),
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add button")
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
    onTrigger: () -> Unit,
) {
    var localX by remember(button.id, button.xFraction) { mutableStateOf(button.xFraction * canvasWidthPx) }
    var localY by remember(button.id, button.yFraction) { mutableStateOf(button.yFraction * canvasHeightPx) }
    var localWidth by remember(button.id, button.widthFraction) { mutableStateOf(button.widthFraction * canvasWidthPx) }
    var localHeight by remember(button.id, button.heightFraction) { mutableStateOf(button.heightFraction * canvasHeightPx) }

    LaunchedEffect(button.xFraction, button.yFraction, button.widthFraction, button.heightFraction, canvasWidthPx, canvasHeightPx) {
        localX = button.xFraction * canvasWidthPx
        localY = button.yFraction * canvasHeightPx
        localWidth = button.widthFraction * canvasWidthPx
        localHeight = button.heightFraction * canvasHeightPx
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
                onClick = { if (editMode) onEdit() else onTrigger() },
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
            Text(
                text = button.label,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (button.serviceUuid.isBlank()) "No BLE binding" else button.payloadHex,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (editMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .pointerInput(canvasWidthPx, canvasHeightPx) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                localWidth = (localWidth + dragAmount.x).coerceIn(80f, canvasWidthPx - localX)
                                localHeight = (localHeight + dragAmount.y).coerceIn(52f, canvasHeightPx - localY)
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
    onSave: (label: String, serviceUuid: String, characteristicUuid: String, payloadHex: String) -> Unit,
    onDelete: () -> Unit,
) {
    var label by remember(button.id) { mutableStateOf(button.label) }
    var payloadHex by remember(button.id) { mutableStateOf(button.payloadHex) }
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
                OutlinedTextField(
                    value = payloadHex,
                    onValueChange = { payloadHex = it.uppercase() },
                    label = { Text("HEX payload (e.g. 01 or A0FF)") },
                    modifier = Modifier.fillMaxWidth(),
                )
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
                    onSave(label, selectedServiceUuid, selectedCharacteristicUuid, payloadHex)
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

private fun hasAllPermissions(context: Context, permissions: Array<String>): Boolean {
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

