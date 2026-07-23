package com.possingle.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.possingle.app.data.ItemEntity
import com.possingle.app.data.PurchaseEntity
import com.possingle.app.util.asCurrency
import com.possingle.app.viewmodel.POSViewModel

/**
 * The "Purchases" popup: record stock coming in from a supplier. Recording
 * a purchase immediately increases the item's stock (via ViewModel.recordPurchase),
 * so Reports/Stock reflect it right away — no separate "confirm stock" step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDialog(viewModel: POSViewModel, onDismiss: () -> Unit) {
    val allItems by viewModel.allItems.collectAsState()
    val purchases by viewModel.recentPurchases.collectAsState()

    var itemMenuExpanded by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<ItemEntity?>(null) }
    var qtyText by remember { mutableStateOf("") }
    var unitCostText by remember { mutableStateOf("") }
    var supplierName by remember { mutableStateOf("") }
    var supplierPhone by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.85f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Purchases", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                Text("Record Purchase", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))

                ExposedDropdownMenuBox(
                    expanded = itemMenuExpanded,
                    onExpandedChange = { itemMenuExpanded = !itemMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedItem?.name ?: "Select item",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Item") },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = itemMenuExpanded, onDismissRequest = { itemMenuExpanded = false }) {
                        allItems.forEach { item ->
                            DropdownMenuItem(text = { Text(item.name) }, onClick = {
                                selectedItem = item
                                itemMenuExpanded = false
                            })
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText, onValueChange = { qtyText = it },
                        label = { Text("Qty received") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unitCostText, onValueChange = { unitCostText = it },
                        label = { Text("Unit cost") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = supplierName, onValueChange = { supplierName = it },
                        label = { Text("Supplier name") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = supplierPhone, onValueChange = { supplierPhone = it },
                        label = { Text("Supplier phone") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val item = selectedItem
                    val qty = qtyText.toDoubleOrNull()
                    val unitCost = unitCostText.toDoubleOrNull()
                    if (item != null && qty != null && qty > 0 && unitCost != null && unitCost >= 0) {
                        viewModel.recordPurchase(item, qty, unitCost, supplierName, supplierPhone.ifBlank { null })
                        qtyText = ""; unitCostText = ""; supplierName = ""; supplierPhone = ""; selectedItem = null
                    }
                }) { Text("Record Purchase & Update Stock") }

                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(8.dp))
                Text("Recent Purchases", style = MaterialTheme.typography.labelLarge)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(purchases, key = { it.id }) { purchase ->
                        PurchaseRow(purchase)
                        Divider()
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("← Back to Settings") }
            }
        }
    }
}

@Composable
private fun PurchaseRow(purchase: PurchaseEntity) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(purchase.itemName, fontWeight = FontWeight.SemiBold)
            Text(
                "${purchase.supplierName.ifBlank { "Unknown supplier" }} · qty ${purchase.qty}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(purchase.totalCost.asCurrency())
    }
}
