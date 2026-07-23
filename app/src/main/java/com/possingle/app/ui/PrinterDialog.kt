package com.possingle.app.ui

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.possingle.app.printer.PaperWidth
import com.possingle.app.printer.PrinterConnectionType
import com.possingle.app.printer.PrinterResult
import com.possingle.app.viewmodel.POSViewModel
import kotlinx.coroutines.launch

/**
 * The "Printer" popup: Bluetooth, USB, LAN, Test Print, 58mm/80mm.
 * Connection type / paper width / LAN IP are all persisted on the ViewModel,
 * so whatever's configured here is exactly what checkout() prints with later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterDialog(viewModel: POSViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val connectionType by viewModel.printerConnectionType.collectAsState()
    val paperWidth by viewModel.paperWidth.collectAsState()
    val lanIp by viewModel.lanPrinterIp.collectAsState()

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var selectedDeviceAddress by remember { mutableStateOf<String?>(null) }
    var hasBluetoothPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
            } else true // pre-Android 12 only needs the manifest permission, already declared
        )
    }

    // Android 12+ requires this permission at runtime before touching bonded devices —
    // skipping this request was the crash you were hitting opening this screen.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBluetoothPermission = granted
        if (granted) {
            pairedDevices = try {
                viewModel.printerManager.pairedBluetoothPrinters()
            } catch (e: SecurityException) {
                emptyList()
            }
        } else {
            statusMessage = "Bluetooth permission denied — pair via Android Bluetooth settings, or use LAN/USB instead."
        }
    }

    LaunchedEffect(hasBluetoothPermission) {
        if (hasBluetoothPermission) {
            pairedDevices = try {
                viewModel.printerManager.pairedBluetoothPrinters()
            } catch (e: SecurityException) {
                emptyList()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Printer Setup") },
        text = {
            Column {
                Text("Connection", style = MaterialTheme.typography.labelLarge)
                Row {
                    PrinterConnectionType.values().forEach { type ->
                        FilterChip(
                            selected = connectionType == type,
                            onClick = { viewModel.setPrinterConnectionType(type) },
                            label = { Text(type.name) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                when (connectionType) {
                    PrinterConnectionType.BLUETOOTH -> {
                        if (!hasBluetoothPermission) {
                            Text("Bluetooth permission needed to see paired printers.")
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = {
                                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            }) { Text("Grant Bluetooth Permission") }
                        } else if (pairedDevices.isEmpty()) {
                            Text("No paired Bluetooth printers found. Pair it in Android Bluetooth settings first.")
                        } else {
                            pairedDevices.forEach { device ->
                                @Suppress("MissingPermission")
                                FilterChip(
                                    selected = selectedDeviceAddress == device.address,
                                    onClick = {
                                        selectedDeviceAddress = device.address
                                        scope.launch {
                                            val result = try {
                                                viewModel.printerManager.connectBluetooth(device)
                                            } catch (e: SecurityException) {
                                                PrinterResult.Failure("Permission error connecting to printer")
                                            }
                                            statusMessage = when (result) {
                                                is PrinterResult.Success -> "Connected to ${device.name}"
                                                is PrinterResult.Failure -> result.message
                                            }
                                        }
                                    },
                                    label = { Text(device.name ?: device.address) },
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                    PrinterConnectionType.LAN -> {
                        OutlinedTextField(
                            value = lanIp, onValueChange = { viewModel.setLanPrinterIp(it) },
                            label = { Text("Printer IP address") }, singleLine = true
                        )
                    }
                    PrinterConnectionType.USB -> {
                        Text("Connect the printer via USB-C/OTG. Vendor-specific setup needed — see PrinterManager.printViaUsb().")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Paper Width", style = MaterialTheme.typography.labelLarge)
                Row {
                    FilterChip(
                        selected = paperWidth == PaperWidth.MM_58,
                        onClick = { viewModel.setPaperWidth(PaperWidth.MM_58) },
                        label = { Text("58mm") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = paperWidth == PaperWidth.MM_80,
                        onClick = { viewModel.setPaperWidth(PaperWidth.MM_80) },
                        label = { Text("80mm") }
                    )
                }

                statusMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val result = try {
                        viewModel.printerManager.testPrint(connectionType, paperWidth, lanIp.ifBlank { null })
                    } catch (e: SecurityException) {
                        PrinterResult.Failure("Permission error — grant Bluetooth permission above and try again")
                    }
                    statusMessage = when (result) {
                        is PrinterResult.Success -> "Test print sent successfully"
                        is PrinterResult.Failure -> "Failed: ${result.message}"
                    }
                }
            }) { Text("Test Print") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
