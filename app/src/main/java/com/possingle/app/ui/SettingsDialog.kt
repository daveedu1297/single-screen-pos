package com.possingle.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.possingle.app.viewmodel.POSViewModel
import com.possingle.app.viewmodel.StoreSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SettingsCategory(val label: String) {
    STORE_INFO("Store Info"),
    SECURITY("Security"),
    BACKUP("Backup"),
    BILLING("Billing"),
    ORDER_TYPES("Order Types"),
    DISPLAY("Display")
}

/**
 * The redesigned Settings screen: a sidebar of categories on the left, the
 * relevant fields for whichever one is selected on the right. The Save
 * button only appears once something has actually changed, so this screen
 * never nags for a no-op tap. Also home to quick-launch buttons for every
 * admin module (Items, Categories, Stock, Reports, Expenses, Purchases) —
 * these used to be separate buttons in the left rail; they all live behind
 * this one PIN gate now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    viewModel: POSViewModel,
    darkModeEnabled: Boolean,
    onToggleDarkMode: () -> Unit,
    onOpenItems: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenStock: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenPurchases: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val saved by viewModel.storeSettings.collectAsState()
    val roundOffMode by viewModel.roundOffMode.collectAsState()
    val pinEnabled by viewModel.pinEnabled.collectAsState()
    val dineInEnabled by viewModel.dineInEnabled.collectAsState()
    val parcelEnabled by viewModel.parcelEnabled.collectAsState()
    val selfServiceEnabled by viewModel.selfServiceEnabled.collectAsState()

    var selectedCategory by remember { mutableStateOf(SettingsCategory.STORE_INFO) }

    // Working copies — the Save button only appears once these differ from `saved`.
    var storeName by remember { mutableStateOf(saved.storeName) }
    var storeAddress by remember { mutableStateOf(saved.storeAddress) }
    var gstin by remember { mutableStateOf(saved.gstin) }
    var invoicePrefix by remember { mutableStateOf(saved.invoicePrefix) }
    var receiptFooter by remember { mutableStateOf(saved.receiptFooter) }
    var newPin by remember { mutableStateOf("") }

    val pendingSettings = StoreSettings(storeName, storeAddress, gstin, invoicePrefix, receiptFooter)
    val hasUnsavedChanges = pendingSettings != saved || newPin.isNotBlank()

    var backupStatus by remember { mutableStateOf<String?>(null) }
    val lastBackup = viewModel.lastBackupMillis

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.backupDatabaseTo(uri) { success, message -> backupStatus = message }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            backupStatus = "Restoring — the app will close automatically. Reopen it after a moment."
            viewModel.restoreDatabaseFrom(uri) { }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.88f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Settings", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))

                Text("Manage", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onOpenItems) { Text("Items") }
                    TextButton(onClick = onOpenCategories) { Text("Categories") }
                    TextButton(onClick = onOpenStock) { Text("Stock") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onOpenReports) { Text("Reports") }
                    TextButton(onClick = onOpenExpenses) { Text("Expenses") }
                    TextButton(onClick = onOpenPurchases) { Text("Purchases") }
                }
                Spacer(Modifier.height(8.dp))
                Divider()
                Spacer(Modifier.height(8.dp))

                Row(modifier = Modifier.weight(1f)) {
                    // --- Left sidebar ---
                    Column(
                        modifier = Modifier
                            .width(150.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        SettingsCategory.values().forEach { cat ->
                            val selected = cat == selectedCategory
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCategory = cat }
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .padding(vertical = 18.dp, horizontal = 12.dp)
                            ) {
                                Text(
                                    cat.label,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    // --- Right detail pane ---
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (selectedCategory) {
                            SettingsCategory.STORE_INFO -> {
                                BigField("Store Name", storeName) { storeName = it }
                                BigField("Address", storeAddress) { storeAddress = it }
                                BigField("GSTIN", gstin) { gstin = it }
                            }
                            SettingsCategory.SECURITY -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                ) {
                                    Text("Require PIN for Settings", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    Switch(checked = pinEnabled, onCheckedChange = { viewModel.setPinEnabled(it) })
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Changing this replaces the current manager PIN immediately.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                BigField("New Manager PIN", newPin) { newPin = it }
                            }
                            SettingsCategory.BACKUP -> {
                                val lastBackupLabel = if (lastBackup > 0)
                                    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(lastBackup))
                                else "Never"
                                Text("Last backup: $lastBackupLabel", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(12.dp))
                                BigButton("Backup Now") {
                                    backupLauncher.launch("whitecrab_backup_${System.currentTimeMillis()}.db")
                                }
                                Spacer(Modifier.height(12.dp))
                                BigButton("Restore from Backup") {
                                    restoreLauncher.launch(arrayOf("*/*"))
                                }
                                backupStatus?.let {
                                    Spacer(Modifier.height(12.dp))
                                    Text(it, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            SettingsCategory.BILLING -> {
                                BigField("Invoice Prefix", invoicePrefix) { invoicePrefix = it }
                                BigField("Receipt Footer Message", receiptFooter) { receiptFooter = it }
                                Spacer(Modifier.height(12.dp))
                                Text("Auto Round-off", style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    com.possingle.app.util.RoundOffMode.values().forEach { mode ->
                                        FilterChip(
                                            selected = roundOffMode == mode,
                                            onClick = { viewModel.setRoundOffMode(mode) },
                                            label = { Text(mode.name) }
                                        )
                                    }
                                }
                                Text(
                                    "NEAREST rounds to the closest rupee, UP/DOWN always round that way, NONE keeps the exact total.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            SettingsCategory.ORDER_TYPES -> {
                                Text(
                                    "Disabled order types won't appear on the billing screen.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(12.dp))
                                OrderTypeToggleRow("Dine-In", dineInEnabled) { viewModel.setOrderTypeEnabled("Dine-In", it) }
                                OrderTypeToggleRow("Parcel", parcelEnabled) { viewModel.setOrderTypeEnabled("Parcel", it) }
                                OrderTypeToggleRow("Self Service", selfServiceEnabled) { viewModel.setOrderTypeEnabled("Self Service", it) }
                            }
                            SettingsCategory.DISPLAY -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                ) {
                                    Text("Dark Mode", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    Switch(checked = darkModeEnabled, onCheckedChange = { onToggleDarkMode() })
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (hasUnsavedChanges) {
                        Button(onClick = {
                            viewModel.saveStoreSettings(pendingSettings)
                            if (newPin.isNotBlank()) {
                                viewModel.setManagerPin(newPin)
                                newPin = ""
                            }
                        }) { Text("Save") }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun OrderTypeToggleRow(label: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

/** A large, touch-friendly text field — tablet-sized tap targets throughout Settings. */
@Composable
private fun BigField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).heightIn(min = 64.dp)
    )
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) { Text(label, style = MaterialTheme.typography.bodyLarge) }
}

/** Placeholder for a future Users/roles admin section (named in roadmap's Admin Features). */
@Composable
fun UsersAdminDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Users") },
        text = { Text("User accounts & role management — extend with a UserEntity/UserDao following the same pattern as CustomerEntity.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
