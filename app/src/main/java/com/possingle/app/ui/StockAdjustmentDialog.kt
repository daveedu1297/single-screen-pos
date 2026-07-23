package com.possingle.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.possingle.app.data.ItemEntity
import com.possingle.app.viewmodel.POSViewModel

/** The "Stock Adjustment" popup: Add/Remove stock with a reason. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAdjustmentDialog(viewModel: POSViewModel, onDismiss: () -> Unit) {
    val items by viewModel.allItems.collectAsState()
    var selectedItem by remember { mutableStateOf<ItemEntity?>(null) }
    var deltaText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var isAdding by remember { mutableStateOf(true) }
    var itemMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stock Adjustment") },
        text = {
            Column {
                Box {
                    OutlinedButton(onClick = { itemMenuExpanded = true }) {
                        Text(selectedItem?.name ?: "Select Item")
                    }
                    DropdownMenu(expanded = itemMenuExpanded, onDismissRequest = { itemMenuExpanded = false }) {
                        items.forEach { item ->
                            DropdownMenuItem(text = { Text("${item.name} (stock ${item.stockQty})") }, onClick = {
                                selectedItem = item; itemMenuExpanded = false
                            })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    FilterChip(selected = isAdding, onClick = { isAdding = true }, label = { Text("Add") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = !isAdding, onClick = { isAdding = false }, label = { Text("Remove") })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = deltaText, onValueChange = { deltaText = it }, label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val qty = deltaText.toDoubleOrNull() ?: 0.0
                val item = selectedItem
                if (item != null && qty > 0) {
                    viewModel.adjustStock(item.id, if (isAdding) qty else -qty, reason.ifBlank { "Manual adjustment" }) {
                        onDismiss()
                    }
                }
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("← Back to Settings") } }
    )
}
