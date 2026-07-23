package com.possingle.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.possingle.app.data.ExpenseEntity
import com.possingle.app.util.asCurrency
import com.possingle.app.viewmodel.POSViewModel

private val DEFAULT_EXPENSE_CATEGORIES = listOf("Rent", "Electricity", "Salaries", "Supplies", "Maintenance", "Other")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDialog(viewModel: POSViewModel, onDismiss: () -> Unit) {
    val expenses by viewModel.expensesForToday().collectAsState(initial = emptyList())
    val categoryTotals by viewModel.expensesByCategoryForToday().collectAsState(initial = emptyList())

    var category by remember { mutableStateOf(DEFAULT_EXPENSE_CATEGORIES.first()) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.85f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Expenses", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                Text("Add Expense", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DEFAULT_EXPENSE_CATEGORIES.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountText, onValueChange = { amountText = it },
                        label = { Text("Amount") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text("Note (optional)") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        viewModel.addExpense(category, amount, note)
                        amountText = ""; note = ""
                    }
                }) { Text("Add Expense") }

                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(8.dp))

                Text("Today by Category", style = MaterialTheme.typography.labelLarge)
                categoryTotals.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(row.categoryName)
                        Text(row.total.asCurrency())
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Today", fontWeight = FontWeight.Bold)
                    Text(categoryTotals.sumOf { it.total }.asCurrency(), fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(16.dp))
                Text("Today's Expenses", style = MaterialTheme.typography.labelLarge)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(expenses, key = { it.id }) { expense ->
                        ExpenseRow(expense, onDelete = { viewModel.deleteExpense(expense) })
                        Divider()
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) { Text("← Back to Settings") }
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(expense.category, fontWeight = FontWeight.SemiBold)
            if (expense.note.isNotBlank()) {
                Text(expense.note, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row {
            Text(expense.amount.asCurrency())
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}
