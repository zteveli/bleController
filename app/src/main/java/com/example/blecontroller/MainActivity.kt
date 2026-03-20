package com.example.blecontroller

import android.Manifest
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.blecontroller.ble.GattCharacteristicUi
import com.example.blecontroller.ble.GattServiceUi
import com.example.blecontroller.data.BleControllerDatabase
import com.example.blecontroller.data.LayoutRepository
import com.example.blecontroller.data.LayoutWithButtons
import com.example.blecontroller.data.VirtualButtonEntity
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

@Composable
private fun BleControllerApp(vm: MainViewModel) {
    val tabs = listOf("BLE", "Controller")
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val statusText by vm.bleManager.statusText.collectAsState()

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
            snackbarHostState.showSnackbar(
                if (granted) "BLE jogosultságok megadva" else "A BLE működéshez jogosultság szükséges",
            )
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
                    requestPermissions = { permissionLauncher.launch(permissions) },
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
    requestPermissions: () -> Unit,
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
                    Text("BLE jogosultságok és vezérlés", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = requestPermissions) {
                            Text("Jogosultságok kérése")
                        }
                        Button(onClick = { if (isScanning) vm.bleManager.stopScan() else vm.bleManager.startScan() }) {
                            Text(if (isScanning) "Scan leállítás" else "Scan indítás")
                        }
                    }
                    if (!vm.bleManager.isBluetoothAvailable()) {
                        Text("A készülék nem támogatja a BLE-t vagy nincs Bluetooth adapter.")
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Aktív kapcsolat", style = MaterialTheme.typography.titleMedium)
                    if (connectedDevice == null) {
                        Text("Nincs csatlakoztatott BLE eszköz.")
                    } else {
                        Text("Eszköz: ${connectedDevice?.name ?: "Ismeretlen"}")
                        Text("Cím: ${connectedDevice?.address}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.bleManager.disconnect() }) {
                                Text("Kapcsolat bontása")
                            }
                            Button(
                                onClick = {
                                    vm.bindCurrentDeviceToSelectedLayout()
                                    showMessage("Az aktuális eszköz hozzárendelve a kiválasztott layouthoz")
                                },
                                enabled = selectedLayout != null,
                            ) {
                                Text("Hozzárendelés a layouthoz")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Talált eszközök", style = MaterialTheme.typography.titleMedium)
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
                        Text(device.name ?: "Névtelen periféria", fontWeight = FontWeight.SemiBold)
                        Text(device.address, style = MaterialTheme.typography.bodySmall)
                        Text("RSSI: ${device.rssi}", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { vm.bleManager.connect(device.address) }) {
                        Text("Kapcsolódás")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Felismerte GATT service-ek", style = MaterialTheme.typography.titleMedium)
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
                Text("Nincs characteristic")
            } else {
                service.characteristics.forEach { ch ->
                    Column {
                        Text(ch.characteristicUuid, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (ch.canWrite) "Írható characteristic" else "Csak olvasható / notify",
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

    val editingButton = selectedLayout?.buttons?.firstOrNull { it.id == editingButtonId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Layoutok", style = MaterialTheme.typography.titleMedium)
                Box {
                    OutlinedButton(onClick = { layoutMenuExpanded = true }) {
                        Text(selectedLayout?.layout?.name ?: "Layout kiválasztása")
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
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Új layout")
                    }
                    IconButton(onClick = { showDuplicateDialog = true }, enabled = selectedLayout != null) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplikálás")
                    }
                    IconButton(onClick = { vm.deleteSelectedLayout() }, enabled = selectedLayout != null && layouts.size > 1) {
                        Icon(Icons.Default.Delete, contentDescription = "Törlés")
                    }
                    IconButton(onClick = { vm.addButton() }, enabled = selectedLayout != null && selectedLayout?.layout?.isLocked == false) {
                        Icon(Icons.Default.Add, contentDescription = "Gomb hozzáadása")
                    }
                    IconButton(onClick = { vm.toggleLock() }, enabled = selectedLayout != null) {
                        Icon(
                            if (selectedLayout?.layout?.isLocked == true) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Zárolás",
                        )
                    }
                    IconButton(onClick = { vm.bindCurrentDeviceToSelectedLayout() }, enabled = selectedLayout != null && connectedDevice != null) {
                        Icon(Icons.Default.Save, contentDescription = "BLE hozzárendelés mentése")
                    }
                }

                selectedLayout?.let { layout ->
                    Text("Layout zárolva: ${if (layout.layout.isLocked) "igen" else "nem"}")
                    Text(
                        "Hozzárendelt BLE eszköz: ${layout.layout.boundDeviceName ?: "—"} (${layout.layout.boundDeviceAddress ?: "—"})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        selectedLayout?.let { layout ->
            ControllerCanvas(
                layout = layout,
                services = services,
                onEditButton = { editingButtonId = it },
                onMoveButton = vm::updateButtonPosition,
                onResizeButton = vm::updateButtonSize,
                onTriggerWrite = { button ->
                    val ok = vm.triggerButtonWrite(button)
                    showMessage(if (ok) "BLE írás elküldve" else "BLE írás nem sikerült")
                },
            )
        } ?: run {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Nincs kiválasztott layout.",
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (showCreateDialog) {
        NameInputDialog(
            title = "Új layout",
            initial = "",
            confirmLabel = "Létrehozás",
            onDismiss = { showCreateDialog = false },
            onConfirm = {
                vm.createLayout(it)
                showCreateDialog = false
            },
        )
    }

    if (showDuplicateDialog && selectedLayout != null) {
        NameInputDialog(
            title = "Layout duplikálása",
            initial = "${selectedLayout.layout.name} másolat",
            confirmLabel = "Mentés",
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
    layout: LayoutWithButtons,
    services: List<GattServiceUi>,
    onEditButton: (Long) -> Unit,
    onMoveButton: (Long, Float, Float) -> Unit,
    onResizeButton: (Long, Float, Float) -> Unit,
    onTriggerWrite: (VirtualButtonEntity) -> Unit,
) {
    val locked = layout.layout.isLocked

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Controller vászon", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (locked) "Élő mód" else "Szerkesztő mód",
                    color = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                "Szerkesztő módban a gombok húzhatók és átméretezhetők. Élő módban koppintáskor BLE characteristic write történik.",
                style = MaterialTheme.typography.bodySmall,
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
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
                text = if (button.serviceUuid.isBlank()) "Nincs BLE hozzárendelés" else button.payloadHex,
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
                label = { Text("Név") },
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
                Text("Mégse")
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
        title = { Text("Virtuális gomb szerkesztése") },
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
                    label = { Text("Gomb felirata") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = payloadHex,
                    onValueChange = { payloadHex = it.uppercase() },
                    label = { Text("HEX payload (pl. 01 vagy A0FF)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Írható characteristic kiválasztása", fontWeight = FontWeight.SemiBold)
                if (writableCharacteristics.isEmpty()) {
                    Text("Nincs írható characteristic. Előbb csatlakozz BLE eszközhöz.")
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
                    Text("Kijelölt service UUID:")
                    AssistChip(onClick = {}, label = { Text(selectedServiceUuid.ifBlank { "—" }) })
                    Text("Kijelölt characteristic UUID:")
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
                Text("Mentés")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) { Text("Törlés") }
                TextButton(onClick = onDismiss) { Text("Bezárás") }
            }
        },
    )
}
