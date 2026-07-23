package com.possingle.app.ui

import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.possingle.app.data.CustomerEntity
import com.possingle.app.viewmodel.POSViewModel

/**
 * The "Customer" popup: Walk-in, Search, Add Customer.
 * Saved customers persist in the app's own database (the "phonebook" the
 * search box pulls from), and you can optionally push a new one straight
 * into the phone's native Contacts app too.
 */
@Composable
fun CustomerDialog(viewModel: POSViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val searchResults by viewModel.customerSearchResults.collectAsState()

    var query by remember { mutableStateOf("") }
    var showAddForm by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customer") },
        text = {
            Column {
                if (!showAddForm) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; viewModel.setCustomerSearchQuery(it) },
                        label = { Text("Search saved customers") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        viewModel.selectCustomer(null) // null = walk-in
                        onDismiss()
                    }) { Text("Use Walk-in Customer") }
                    TextButton(onClick = { showAddForm = true }) { Text("+ Add New Customer") }

                    Spacer(Modifier.height(8.dp))
                    Divider()
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(searchResults, key = { it.id }) { customer ->
                            ListItem(
                                headlineContent = { Text(customer.name) },
                                supportingContent = customer.phone?.let { phone -> { Text(phone) } },
                                modifier = Modifier.clickable {
                                    viewModel.selectCustomer(customer)
                                    onDismiss()
                                }
                            )
                            Divider()
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        label = { Text("Name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPhone, onValueChange = { newPhone = it },
                        label = { Text("Phone") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    // Opens Android's own "Add Contact" screen pre-filled — the person
                    // taps Save there themselves, so nothing is written silently and
                    // no extra Contacts permission is needed.
                    TextButton(onClick = {
                        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                            type = ContactsContract.RawContacts.CONTENT_TYPE
                            putExtra(ContactsContract.Intents.Insert.NAME, newName)
                            putExtra(ContactsContract.Intents.Insert.PHONE, newPhone)
                        }
                        context.startActivity(intent)
                    }) { Text("Also save to Phone Contacts") }
                }
            }
        },
        confirmButton = {
            if (showAddForm) {
                TextButton(onClick = {
                    val nameToSave = newName
                    val phoneToSave = newPhone.ifBlank { null }
                    viewModel.addCustomer(nameToSave, phoneToSave) { created ->
                        viewModel.selectCustomer(created)
                    }
                    newName = ""
                    newPhone = ""
                    onDismiss()
                }) { Text("Save & Select") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (showAddForm) {
                TextButton(onClick = {
                    newName = ""
                    newPhone = ""
                    showAddForm = false
                }) { Text("Back") }
            }
        }
    )
}
