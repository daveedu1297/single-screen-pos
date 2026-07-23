package com.possingle.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.possingle.app.data.CategoryEntity
import com.possingle.app.data.ItemEntity
import com.possingle.app.viewmodel.POSViewModel

@Composable
fun ItemsAdminDialog(viewModel: POSViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val items by viewModel.allItems.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var editingItem by remember { mutableStateOf<ItemEntity?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }

    val templateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(POSViewModel.CSV_TEMPLATE.toByteArray())
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text != null) {
                viewModel.importInventoryCsv(text) { imported, skipped ->
                    importStatus = "Imported $imported item(s)" + if (skipped > 0) ", skipped $skipped" else ""
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = androidx.compose.ui.Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.85f)) {
            Column(modifier = androidx.compose.ui.Modifier.padding(16.dp)) {
                Row(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Manage Items", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { isCreatingNew = true }) { Icon(Icons.Default.Add, "Add Item") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { templateLauncher.launch("inventory_template.csv") }) { Text("Download Sample Template") }
                    TextButton(onClick = { importLauncher.launch(arrayOf("text/*", "text/csv", "*/*")) }) { Text("Import from CSV") }
                }
                importStatus?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                    items(items, key = { it.id }) { item ->
                        ListItem(
                            headlineContent = { Text(item.name) },
                            supportingContent = { Text("₹${item.price} · GST ${item.gstPercent}% · Stock ${item.stockQty}") },
                            trailingContent = {
                                TextButton(onClick = { editingItem = item }) { Text("Edit") }
                            }
                        )
                        Divider()
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss) { Text("← Back to Settings") }
            }
        }
    }

    if (isCreatingNew) {
        AddEditItemDialog(
            categories = categories,
            existing = null,
            onSave = { newItem -> viewModel.saveItem(newItem) { isCreatingNew = false } },
            onDismiss = { isCreatingNew = false },
            onCreateCategory = { viewModel.addCategory(it) }
        )
    }
    editingItem?.let { item ->
        AddEditItemDialog(
            categories = categories,
            existing = item,
            onSave = { updated -> viewModel.saveItem(updated) { editingItem = null } },
            onDelete = { viewModel.deleteItem(item.id) { editingItem = null } },
            onDismiss = { editingItem = null },
            onCreateCategory = { viewModel.addCategory(it) }
        )
    }
}

/** The "Add Item" / "Edit Item" popup from the roadmap: Name, Category, Prices, GST, Barcode, Image, Stock. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AddEditItemDialog(
    categories: List<CategoryEntity>,
    existing: ItemEntity?,
    onSave: (ItemEntity) -> Unit,
    onDismiss: () -> Unit,
    onCreateCategory: (String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var shortCode by remember { mutableStateOf(existing?.shortCode ?: "") }
    var price by remember { mutableStateOf(existing?.price?.toString() ?: "") }
    var gst by remember { mutableStateOf(existing?.gstPercent ?: 5.0) }
    var barcode by remember { mutableStateOf(existing?.barcode ?: "") }
    var stock by remember { mutableStateOf(existing?.stockQty?.toString() ?: "0") }
    var minStockWarning by remember { mutableStateOf(existing?.minStockWarning?.toString() ?: "5") }
    var isVeg by remember { mutableStateOf(existing?.isVeg ?: true) }
    var parcelChargeEnabled by remember { mutableStateOf(existing?.parcelChargeEnabled ?: false) }
    var parcelChargeAmount by remember { mutableStateOf(existing?.parcelChargeAmount?.toString() ?: "0") }
    var categoryId by remember { mutableStateOf(existing?.categoryId ?: categories.firstOrNull()?.id ?: 0L) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var showNewCategoryField by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Item" else "Edit Item") },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Item Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = shortCode, onValueChange = { shortCode = it }, label = { Text("Short Code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))

                Text("Category", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        OutlinedButton(onClick = { categoryMenuExpanded = true }) {
                            Text(categories.firstOrNull { it.id == categoryId }?.name ?: "Select Category")
                        }
                        DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                            categories.forEach { cat ->
                                DropdownMenuItem(text = { Text(cat.name) }, onClick = {
                                    categoryId = cat.id; categoryMenuExpanded = false
                                })
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showNewCategoryField = !showNewCategoryField }) { Text("+ New Category") }
                }
                if (showNewCategoryField) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newCategoryName, onValueChange = { newCategoryName = it },
                            label = { Text("New category name") }, singleLine = true, modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            if (newCategoryName.isNotBlank()) {
                                onCreateCategory(newCategoryName.trim())
                                newCategoryName = ""
                                showNewCategoryField = false
                            }
                        }) { Text("Add") }
                    }
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = price, onValueChange = { price = it }, label = { Text("Price (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                Text("GST %", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.0, 5.0, 12.0, 18.0, 28.0).forEach { rate ->
                        FilterChip(selected = gst == rate, onClick = { gst = rate }, label = { Text("${rate.toInt()}%") })
                    }
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("Barcode (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stock, onValueChange = { stock = it }, label = { Text("Opening Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minStockWarning, onValueChange = { minStockWarning = it }, label = { Text("Min Stock Warning") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Veg / Non-Veg", modifier = Modifier.weight(1f))
                    FilterChip(selected = isVeg, onClick = { isVeg = true }, label = { Text("Veg") })
                    Spacer(Modifier.width(4.dp))
                    FilterChip(selected = !isVeg, onClick = { isVeg = false }, label = { Text("Non-Veg") })
                }
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Parcel Charge", modifier = Modifier.weight(1f))
                    Switch(checked = parcelChargeEnabled, onCheckedChange = { parcelChargeEnabled = it })
                }
                if (parcelChargeEnabled) {
                    OutlinedTextField(
                        value = parcelChargeAmount, onValueChange = { parcelChargeAmount = it },
                        label = { Text("Parcel Charge Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Image picker: wire up an ActivityResultContracts.GetContent() launcher
                // in the hosting Activity and pass the picked URI's path in here.
                Text("Image: attach via device gallery (hook up in Activity)", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    ItemEntity(
                        id = existing?.id ?: 0L,
                        name = name,
                        categoryId = categoryId,
                        price = price.toDoubleOrNull() ?: 0.0,
                        gstPercent = gst,
                        shortCode = shortCode,
                        barcode = barcode.ifBlank { null },
                        imagePath = existing?.imagePath,
                        stockQty = stock.toDoubleOrNull() ?: 0.0,
                        minStockWarning = minStockWarning.toDoubleOrNull() ?: 5.0,
                        isVeg = isVeg,
                        parcelChargeEnabled = parcelChargeEnabled,
                        parcelChargeAmount = parcelChargeAmount.toDoubleOrNull() ?: 0.0,
                        isFavorite = existing?.isFavorite ?: false
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
