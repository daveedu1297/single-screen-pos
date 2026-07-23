package com.possingle.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.possingle.app.data.BillWithLines
import com.possingle.app.viewmodel.POSViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live Kitchen/Order Board. Every checkout automatically appears here as an
 * open order; each line item can be ticked off as served, and the whole
 * order is closed (removed from the board) once fulfilled. Backed by a Room
 * Flow, so it updates in real time across the tablet without any manual
 * refresh — a new bill from checkout shows up here immediately.
 */
@Composable
fun LiveOrderBoardDialog(viewModel: POSViewModel, onDismiss: () -> Unit) {
    val openOrders by viewModel.openOrders.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Live Orders", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Spacer(Modifier.height(8.dp))

                if (openOrders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No open orders. New orders appear here automatically after checkout.")
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(openOrders, key = { it.bill.id }) { order ->
                            OrderCard(
                                order = order,
                                onToggleServed = { lineId, served -> viewModel.setLineServed(lineId, served) },
                                onCloseOrder = { viewModel.closeOrder(order.bill.id) },
                                modifier = Modifier.width(260.dp).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(
    order: BillWithLines,
    onToggleServed: (lineId: Long, served: Boolean) -> Unit,
    onCloseOrder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allServed = order.lines.isNotEmpty() && order.lines.all { it.served }
    val timeLabel = remember(order.bill.timestampMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(order.bill.timestampMillis))
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(order.bill.customerName, fontWeight = FontWeight.Bold, maxLines = 1)
                order.bill.customerPhone?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(timeLabel, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(order.lines, key = { it.id }) { line ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = line.served,
                        onCheckedChange = { checked -> onToggleServed(line.id, checked) }
                    )
                    Text(
                        text = "${line.itemName} x${line.qty}",
                        textDecoration = if (line.served) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onCloseOrder,
            enabled = allServed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (allServed) "Close Order" else "Tick all items to close")
        }
    }
}
