package com.possingle.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.possingle.app.viewmodel.POSViewModel

/** Lets a manager add or remove item categories (e.g. Beverages, Snacks). */
@Composable
fun CategoryManagementDialog(viewModel: POSViewModel, onDismiss: () -> Unit) {
    val categories by viewModel.categories.collectAsState()
    var newCategoryName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Categories") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("New category name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        if (newCategoryName.isNotBlank()) {
                            viewModel.addCategory(newCategoryName.trim())
                            newCategoryName = ""
                        }
                    }) { Text("Add") }
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(categories, key = { it.id }) { category ->
                        ListItem(
                            headlineContent = { Text(category.name) },
                            trailingContent = {
                                IconButton(onClick = { viewModel.deleteCategory(category) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete category")
                                }
                            }
                        )
                        Divider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("← Back to Settings") } }
    )
}
