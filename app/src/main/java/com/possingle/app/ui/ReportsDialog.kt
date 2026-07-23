package com.possingle.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.possingle.app.data.BillEntity
import com.possingle.app.data.BillLineEntity
import com.possingle.app.data.CategorySalesRow
import com.possingle.app.data.ItemSalesRow
import com.possingle.app.data.PaymentSummaryRow
import com.possingle.app.util.PdfReportGenerator
import com.possingle.app.util.asCurrency
import com.possingle.app.viewmodel.CartLine
import com.possingle.app.viewmodel.POSViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The "Reports" popup: Bills, Sales Summary, Item-wise, Category-wise, All Reports. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsDialog(viewModel: POSViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val storeSettings by viewModel.storeSettings.collectAsState()
    val bills by viewModel.billsForToday().collectAsState(initial = emptyList())
    val paymentSummary by viewModel.paymentSummaryForToday().collectAsState(initial = emptyList())
    val itemizedSales by viewModel.itemizedSalesForToday().collectAsState(initial = emptyList())
    val categorySales by viewModel.categorySalesForToday().collectAsState(initial = emptyList())
    val expensesTotal by viewModel.expensesTotalForToday().collectAsState(initial = 0.0)
    val purchasesTotal by viewModel.purchasesTotalForToday().collectAsState(initial = 0.0)

    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Bills", "Sales Summary", "Item-wise", "Category-wise", "All Reports")

    var viewingBill by remember { mutableStateOf<BillEntity?>(null) }
    var viewingLines by remember { mutableStateOf<List<BillLineEntity>>(emptyList()) }

    fun exportAndShare(file: java.io.File) = PdfReportGenerator.sharePdf(context, file)

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.85f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Today's Reports", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))

                TabRow(selectedTabIndex = tabIndex) {
                    tabs.forEachIndexed { index, label ->
                        Tab(selected = tabIndex == index, onClick = { tabIndex = index }, text = { Text(label) })
                    }
                }
                Spacer(Modifier.height(12.dp))

                Box(modifier = Modifier.weight(1f)) {
                    when (tabIndex) {
                        0 -> BillsTab(bills = bills, onBillClick = { bill ->
                            viewModel.loadBillLines(bill.id) { lines ->
                                viewingBill = bill
                                viewingLines = lines
                            }
                        })
                        1 -> SalesSummaryTab(
                            bills = bills, paymentSummary = paymentSummary,
                            expensesTotal = expensesTotal, purchasesTotal = purchasesTotal,
                            onExport = {
                                exportAndShare(
                                    PdfReportGenerator.generateSalesSummary(
                                        context, storeSettings.storeName, bills, paymentSummary, expensesTotal, purchasesTotal
                                    )
                                )
                            }
                        )
                        2 -> ItemizedSalesTab(itemizedSales, onExport = {
                            exportAndShare(PdfReportGenerator.generateItemWiseReport(context, storeSettings.storeName, itemizedSales))
                        })
                        3 -> CategorySalesTab(categorySales, onExport = {
                            exportAndShare(PdfReportGenerator.generateCategoryWiseReport(context, storeSettings.storeName, categorySales))
                        })
                        else -> AllReportsTab(onExport = {
                            exportAndShare(
                                PdfReportGenerator.generateAllReports(
                                    context, storeSettings.storeName, bills, paymentSummary,
                                    itemizedSales, categorySales, expensesTotal, purchasesTotal
                                )
                            )
                        })
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("← Back to Settings") }
                }
            }
        }
    }

    viewingBill?.let { bill ->
        BillDetailDialog(
            viewModel = viewModel,
            bill = bill,
            lines = viewingLines,
            onReprint = { viewModel.reprintBill(bill, viewingLines) },
            onDismiss = { viewingBill = null }
        )
    }
}

@Composable
private fun SalesSummaryTab(
    bills: List<BillEntity>,
    paymentSummary: List<PaymentSummaryRow>,
    expensesTotal: Double,
    purchasesTotal: Double,
    onExport: () -> Unit
) {
    val totalSales = bills.sumOf { it.grandTotal }
    val totalGst = bills.sumOf { it.gstTotal }
    Column {
        TextButton(onClick = onExport) { Text("Export Sales Summary PDF") }
        Spacer(Modifier.height(8.dp))
        SummaryRow("Bills today", bills.size.toString())
        SummaryRow("Total Sales", totalSales.asCurrency())
        SummaryRow("Total GST", totalGst.asCurrency())
        SummaryRow("Total Expenses", expensesTotal.asCurrency())
        SummaryRow("Total Purchases", purchasesTotal.asCurrency())
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        SummaryRow("Net (Sales − Expenses − Purchases)", (totalSales - expensesTotal - purchasesTotal).asCurrency(), bold = true)
        Spacer(Modifier.height(16.dp))
        Text("Payment Mode", style = MaterialTheme.typography.labelLarge)
        LazyColumn {
            items(paymentSummary) { row ->
                SummaryRow("${row.paymentMode} (${row.count})", row.total.asCurrency())
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ItemizedSalesTab(rows: List<ItemSalesRow>, onExport: () -> Unit) {
    Column {
        TextButton(onClick = onExport) { Text("Export Item-wise PDF") }
        if (rows.isEmpty()) {
            Text("No items sold yet today.")
            return
        }
        LazyColumn {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Item", style = MaterialTheme.typography.labelLarge)
                    Text("Qty · Revenue", style = MaterialTheme.typography.labelLarge)
                }
                Divider()
            }
            items(rows) { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.itemName)
                    Text("${row.totalQty} · ${row.totalRevenue.asCurrency()}")
                }
                Divider()
            }
        }
    }
}

@Composable
private fun CategorySalesTab(rows: List<CategorySalesRow>, onExport: () -> Unit) {
    Column {
        TextButton(onClick = onExport) { Text("Export Category-wise PDF") }
        if (rows.isEmpty()) {
            Text("No category sales yet today.")
            return
        }
        LazyColumn {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    Text("Qty · Revenue", style = MaterialTheme.typography.labelLarge)
                }
                Divider()
            }
            items(rows) { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.categoryName)
                    Text("${row.totalQty} · ${row.totalRevenue.asCurrency()}")
                }
                Divider()
            }
        }
    }
}

@Composable
private fun AllReportsTab(onExport: () -> Unit) {
    Column {
        Text(
            "Bundles Sales Summary, Item-wise and Category-wise into one PDF.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onExport) { Text("Export All Reports PDF") }
    }
}

@Composable
private fun BillsTab(bills: List<BillEntity>, onBillClick: (BillEntity) -> Unit) {
    if (bills.isEmpty()) {
        Text("No bills yet today.")
        return
    }
    LazyColumn {
        items(bills, key = { it.id }) { bill ->
            val timeLabel = remember(bill.timestampMillis) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(bill.timestampMillis))
            }
            ListItem(
                headlineContent = { Text("Bill #${bill.id} — ${bill.customerName}") },
                supportingContent = { Text("$timeLabel · ${bill.paymentMode}") },
                trailingContent = { Text(bill.grandTotal.asCurrency()) },
                modifier = Modifier.clickable { onBillClick(bill) }
            )
            Divider()
        }
    }
}

/**
 * Shows the bill styled like the actual printed receipt would look (narrow,
 * monospace, dashed separators) rather than a plain data list — plus
 * Reprint and Edit Bill actions.
 */
@Composable
private fun BillDetailDialog(
    viewModel: POSViewModel,
    bill: BillEntity,
    lines: List<BillLineEntity>,
    onReprint: () -> Unit,
    onDismiss: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    val storeSettings by viewModel.storeSettings.collectAsState()
    val charsPerLine = 32 // mirrors 58mm thermal paper width

    fun padLine(left: String, right: String): String {
        val padding = (charsPerLine - left.length - right.length).coerceAtLeast(1)
        return left + " ".repeat(padding) + right
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.85f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Bill #${bill.id}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                // A narrow, monospace "physical receipt" preview.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.White),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .width(280.dp)
                            .padding(vertical = 12.dp)
                    ) {
                        val mono = FontFamily.Monospace
                        Text(storeSettings.storeName, fontFamily = mono, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
                        if (storeSettings.storeAddress.isNotBlank()) {
                            Text(storeSettings.storeAddress, fontFamily = mono, color = Color.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                        if (storeSettings.gstin.isNotBlank()) {
                            Text("GSTIN: ${storeSettings.gstin}", fontFamily = mono, color = Color.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                        Text("Bill: ${storeSettings.invoicePrefix}-${bill.id}", fontFamily = mono, color = Color.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text("${bill.customerName}${bill.customerPhone?.let { " · $it" } ?: ""}", fontFamily = mono, color = Color.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text("-".repeat(charsPerLine), fontFamily = mono, color = Color.Black)
                        lines.forEach { line ->
                            Text(
                                padLine("${line.itemName} x${line.qty}", String.format("%.2f", line.lineTotal)),
                                fontFamily = mono, color = Color.Black
                            )
                        }
                        Text("-".repeat(charsPerLine), fontFamily = mono, color = Color.Black)
                        Text(padLine("Subtotal:", String.format("%.2f", bill.subtotal)), fontFamily = mono, color = Color.Black)
                        val halfGst = bill.gstTotal / 2.0
                        Text(padLine("CGST:", String.format("%.2f", halfGst)), fontFamily = mono, color = Color.Black)
                        Text(padLine("SGST:", String.format("%.2f", halfGst)), fontFamily = mono, color = Color.Black)
                        if (bill.parcelCharge > 0.0) {
                            Text(padLine("Parcel Charge:", String.format("%.2f", bill.parcelCharge)), fontFamily = mono, color = Color.Black)
                        }
                        if (bill.roundOffAdjustment != 0.0) {
                            val sign = if (bill.roundOffAdjustment > 0) "+" else ""
                            Text(padLine("Round off:", sign + String.format("%.2f", bill.roundOffAdjustment)), fontFamily = mono, color = Color.Black)
                        }
                        Text(padLine("TOTAL:", String.format("%.2f", bill.grandTotal)), fontFamily = mono, fontWeight = FontWeight.Bold, color = Color.Black)
                        Spacer(Modifier.height(8.dp))
                        Text(storeSettings.receiptFooter, fontFamily = mono, color = Color.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { editing = true }) { Text("Edit Bill") }
                    TextButton(onClick = onReprint) { Text("Reprint") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }

    if (editing) {
        BillEditDialog(
            viewModel = viewModel,
            bill = bill,
            originalLines = lines,
            onDismiss = { editing = false; onDismiss() }
        )
    }
}

@Composable
private fun BillEditDialog(
    viewModel: POSViewModel,
    bill: BillEntity,
    originalLines: List<BillLineEntity>,
    onDismiss: () -> Unit
) {
    val allItems by viewModel.allItems.collectAsState()
    var editableLines by remember {
        mutableStateOf(
            originalLines.map { line ->
                val item = allItems.find { it.id == line.itemId }
                    ?: com.possingle.app.data.ItemEntity(
                        id = line.itemId, name = line.itemName, categoryId = 0,
                        price = line.unitPrice, gstPercent = line.gstPercent
                    )
                CartLine(item, line.qty)
            }.toMutableList()
        )
    }
    var addItemMenuExpanded by remember { mutableStateOf(false) }

    val subtotal = editableLines.sumOf { it.lineSubtotal }
    val gstTotal = editableLines.sumOf { it.lineGst }
    val total = subtotal + gstTotal

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.85f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Edit Bill #${bill.id}", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Adjusts stock automatically to match your changes, then reprints.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(editableLines, key = { it.item.id }) { line ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(line.item.name, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                editableLines = editableLines.map {
                                    if (it.item.id == line.item.id) it.copy(qty = (it.qty - 1).coerceAtLeast(0.0)) else it
                                }.filter { it.qty > 0.0 }.toMutableList()
                            }) { Text("-") }
                            Text("${line.qty}")
                            IconButton(onClick = {
                                editableLines = editableLines.map {
                                    if (it.item.id == line.item.id) it.copy(qty = it.qty + 1) else it
                                }.toMutableList()
                            }) { Text("+") }
                            TextButton(onClick = {
                                editableLines = editableLines.filterNot { it.item.id == line.item.id }.toMutableList()
                            }) { Text("Remove") }
                        }
                        Divider()
                    }
                }

                Spacer(Modifier.height(8.dp))
                Box {
                    TextButton(onClick = { addItemMenuExpanded = true }) { Text("+ Add Item") }
                    DropdownMenu(expanded = addItemMenuExpanded, onDismissRequest = { addItemMenuExpanded = false }) {
                        allItems.forEach { item ->
                            DropdownMenuItem(text = { Text(item.name) }, onClick = {
                                val existingIdx = editableLines.indexOfFirst { it.item.id == item.id }
                                editableLines = if (existingIdx >= 0) {
                                    editableLines.mapIndexed { i, l -> if (i == existingIdx) l.copy(qty = l.qty + 1) else l }.toMutableList()
                                } else {
                                    (editableLines + CartLine(item, 1.0)).toMutableList()
                                }
                                addItemMenuExpanded = false
                            })
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(8.dp))
                SummaryRow("Subtotal", subtotal.asCurrency())
                SummaryRow("GST", gstTotal.asCurrency())
                SummaryRow("Total (before round-off)", total.asCurrency(), bold = true)

                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        viewModel.editBill(bill, originalLines, editableLines) { onDismiss() }
                    }) { Text("Save & Reprint") }
                }
            }
        }
    }
}
