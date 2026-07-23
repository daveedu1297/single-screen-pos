package com.possingle.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.possingle.app.data.CategoryEntity
import com.possingle.app.data.ItemEntity
import com.possingle.app.util.asCurrency
import com.possingle.app.viewmodel.AdminSection
import com.possingle.app.viewmodel.CartLine
import com.possingle.app.viewmodel.POSViewModel
import kotlin.math.roundToInt

/**
 * The entire app lives on this one screen, per the roadmap: top bar, left
 * category/favorites rail, center item grid, right cart+payment panel.
 * Every popup (add item, stock, customer, printer, reports, settings) is a
 * dialog launched from here rather than a separate screen/navigation route.
 */
@Composable
fun POSScreen(
    viewModel: POSViewModel,
    darkModeEnabled: Boolean,
    onToggleDarkMode: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val items by viewModel.visibleItems.collectAsState()
    val cartLines by viewModel.cartLines.collectAsState()
    val cartTotals by viewModel.cartTotals.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val selectedCustomer by viewModel.selectedCustomer.collectAsState()
    val dineInEnabled by viewModel.dineInEnabled.collectAsState()
    val parcelEnabled by viewModel.parcelEnabled.collectAsState()
    val selfServiceEnabled by viewModel.selfServiceEnabled.collectAsState()
    val selectedOrderType by viewModel.selectedOrderType.collectAsState()

    // Which popup is currently showing (only one at a time — keeps the "single screen" promise).
    var activeDialog by remember { mutableStateOf<String?>(null) }
    var pendingAdminSection by remember { mutableStateOf<AdminSection?>(null) }
    var longPressedItem by remember { mutableStateOf<ItemEntity?>(null) }

    val pinEnabled by viewModel.pinEnabled.collectAsState()

    fun requestAdmin(section: AdminSection) {
        if (pinEnabled) {
            pendingAdminSection = section
            activeDialog = "pin"
        } else {
            activeDialog = section.name.lowercase()
        }
    }

    Scaffold(
        topBar = {
            PosTopBar(
                onSearchChanged = viewModel::setSearchQuery,
                onShortCodeSearchChanged = viewModel::setShortCodeQuery,
                onBarcodeClick = { activeDialog = "barcode" },
                onCustomerClick = { activeDialog = "customer" },
                onPrinterClick = { activeDialog = "printer" },
                onSettingsClick = { requestAdmin(AdminSection.SETTINGS) },
                onLiveOrdersClick = { activeDialog = "liveorders" },
                customerLabel = selectedCustomer?.name ?: "Walk-in"
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OrderTypeSelector(
                dineInEnabled = dineInEnabled,
                parcelEnabled = parcelEnabled,
                selfServiceEnabled = selfServiceEnabled,
                selected = selectedOrderType,
                onSelect = viewModel::selectOrderType
            )
            Row(modifier = Modifier.fillMaxSize()) {

            // LEFT PANEL — categories & favorites only; admin tools live in Settings now
            CategoryRail(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                onSelectCategory = viewModel::selectCategory,
                onFavoritesClick = viewModel::toggleFavoritesOnly,
                onReorder = viewModel::reorderCategories,
                modifier = Modifier.width(140.dp).fillMaxHeight()
            )

            // CENTER — item grid with two-tap add + long-press quick actions
            ItemGrid(
                items = items,
                onItemTap = { viewModel.addToCart(it) },
                onItemLongPress = { longPressedItem = it },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )

            // RIGHT PANEL — cart, totals, payment, print
            CartPanel(
                lines = cartLines,
                totals = cartTotals,
                onQtyChange = viewModel::updateCartQty,
                onRemove = viewModel::removeFromCart,
                onCheckout = { paymentMode ->
                    viewModel.checkout(paymentMode) { activeDialog = "receipt" }
                },
                modifier = Modifier.width(320.dp).fillMaxHeight()
            )
            }
        }
    }

    // --- Popups ---
    when (activeDialog) {
        "pin" -> ManagerPinDialog(
            onDismiss = { activeDialog = null },
            onVerify = { pin ->
                if (viewModel.verifyPin(pin)) {
                    activeDialog = pendingAdminSection?.name?.lowercase()
                    true
                } else false
            }
        )
        "items" -> ItemsAdminDialog(viewModel = viewModel, onDismiss = { activeDialog = "settings" })
        "categories" -> CategoryManagementDialog(viewModel = viewModel, onDismiss = { activeDialog = "settings" })
        "stock" -> StockAdjustmentDialog(viewModel = viewModel, onDismiss = { activeDialog = "settings" })
        "reports" -> ReportsDialog(viewModel = viewModel, onDismiss = { activeDialog = "settings" })
        "settings" -> SettingsDialog(
            viewModel = viewModel,
            darkModeEnabled = darkModeEnabled,
            onToggleDarkMode = onToggleDarkMode,
            onOpenItems = { activeDialog = "items" },
            onOpenCategories = { activeDialog = "categories" },
            onOpenStock = { activeDialog = "stock" },
            onOpenReports = { activeDialog = "reports" },
            onOpenExpenses = { activeDialog = "expenses" },
            onOpenPurchases = { activeDialog = "purchases" },
            onDismiss = { activeDialog = null }
        )
        "expenses" -> ExpenseDialog(viewModel = viewModel, onDismiss = { activeDialog = "settings" })
        "purchases" -> PurchaseDialog(viewModel = viewModel, onDismiss = { activeDialog = "settings" })
        "users" -> UsersAdminDialog(onDismiss = { activeDialog = null })
        "customer" -> CustomerDialog(
            viewModel = viewModel,
            onDismiss = { activeDialog = null }
        )
        "printer" -> PrinterDialog(viewModel = viewModel, onDismiss = { activeDialog = null })
        "liveorders" -> LiveOrderBoardDialog(viewModel = viewModel, onDismiss = { activeDialog = null })
        "barcode" -> BarcodeScanDialog(
            viewModel = viewModel,
            onDismiss = { activeDialog = null }
        )
        "receipt" -> AlertDialog(
            onDismissRequest = { activeDialog = null },
            confirmButton = { TextButton(onClick = { activeDialog = null }) { Text("Next Bill") } },
            title = { Text("Bill Complete") },
            text = { Text("Receipt sent to printer. Ready for next bill.") }
        )
    }

    longPressedItem?.let { item ->
        QuickActionSheet(
            item = item,
            onDismiss = { longPressedItem = null },
            onEdit = { longPressedItem = null; requestAdmin(AdminSection.ITEMS) },
            onStock = { longPressedItem = null; requestAdmin(AdminSection.STOCK) },
            onToggleFavorite = { viewModel.toggleFavorite(item); longPressedItem = null },
            onDelete = { viewModel.deleteItem(item.id) {}; longPressedItem = null }
        )
    }
}

/**
 * Simple "WC" (White Crab) monogram badge for the top bar. Swap this for an
 * Image(painterResource(...)) once you have an actual logo file to drop in.
 */
@Composable
private fun WhiteCrabLogo() {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "WC",
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PosTopBar(
    onSearchChanged: (String) -> Unit,
    onShortCodeSearchChanged: (String) -> Unit,
    onBarcodeClick: () -> Unit,
    onCustomerClick: () -> Unit,
    onPrinterClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLiveOrdersClick: () -> Unit,
    customerLabel: String
) {
    var searchText by remember { mutableStateOf("") }
    var shortCodeText by remember { mutableStateOf("") }
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                WhiteCrabLogo()
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it; onSearchChanged(it) },
                    placeholder = { Text("Item name…") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search by name") },
                    modifier = Modifier.weight(1f).height(56.dp)
                )
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = shortCodeText,
                    onValueChange = { shortCodeText = it; onShortCodeSearchChanged(it) },
                    placeholder = { Text("Short code…") },
                    singleLine = true,
                    modifier = Modifier.width(140.dp).height(56.dp)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onBarcodeClick) { Icon(Icons.Default.QrCodeScanner, "Barcode") }
                TextButton(onClick = onLiveOrdersClick) {
                    Icon(Icons.Default.Restaurant, contentDescription = "Live Orders")
                    Spacer(Modifier.width(4.dp))
                    Text("Live Orders")
                }
                TextButton(onClick = onCustomerClick) {
                    Icon(Icons.Default.Person, contentDescription = "Customer")
                    Spacer(Modifier.width(4.dp))
                    Text(customerLabel)
                }
                IconButton(onClick = onPrinterClick) { Icon(Icons.Default.Print, "Printer") }
                IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "Settings") }
            }
        }
    )
}

/** Row of order-type chips at the top of the billing screen — only enabled types show. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderTypeSelector(
    dineInEnabled: Boolean,
    parcelEnabled: Boolean,
    selfServiceEnabled: Boolean,
    selected: String,
    onSelect: (String) -> Unit
) {
    val types = buildList {
        if (dineInEnabled) add("Dine-In")
        if (parcelEnabled) add("Parcel")
        if (selfServiceEnabled) add("Self Service")
    }
    if (types.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        types.forEach { type ->
            FilterChip(selected = selected == type, onClick = { onSelect(type) }, label = { Text(type) })
        }
    }
}

@Composable
private fun CategoryRail(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    onSelectCategory: (Long?) -> Unit,
    onFavoritesClick: () -> Unit,
    onReorder: (List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var orderedCategories by remember(categories) { mutableStateOf(categories) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val rowHeight = 48.dp
    val rowHeightPx = with(density) { rowHeight.toPx() }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        TextButton(onClick = onFavoritesClick, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Star, contentDescription = null)
            Spacer(Modifier.width(4.dp)); Text("Favorites")
        }
        TextButton(onClick = { onSelectCategory(null) }, modifier = Modifier.fillMaxWidth()) {
            Text("All Items")
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(orderedCategories, key = { it.id }) { category ->
                val selected = category.id == selectedCategoryId
                val isDragging = draggingId == category.id
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "categoryDragElevation")
                val scale by animateFloatAsState(if (isDragging) 1.05f else 1f, label = "categoryDragScale")
                val offsetDp = with(density) { (if (isDragging) dragOffsetPx else 0f).toDp() }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .zIndex(if (isDragging) 1f else 0f)
                        .offset(y = offsetDp)
                        .scale(scale)
                        .shadow(elevation)
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(horizontal = 12.dp)
                        .pointerInput(category.id) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downTimeMillis = System.currentTimeMillis()
                                var longPressActivated = false
                                var releasedEarly = false
                                // Wait up to 3 seconds — if the finger lifts first, it's a normal tap.
                                while (!longPressActivated && !releasedEarly) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) {
                                        releasedEarly = true
                                    } else if (System.currentTimeMillis() - downTimeMillis >= 3000L) {
                                        longPressActivated = true
                                    }
                                }
                                if (releasedEarly) {
                                    onSelectCategory(category.id)
                                } else if (longPressActivated) {
                                    draggingId = category.id
                                    dragOffsetPx = 0f
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        dragOffsetPx += change.positionChange().y
                                        change.consume()
                                        val moveBy = (dragOffsetPx / rowHeightPx).roundToInt()
                                        if (moveBy != 0) {
                                            val currentIndex = orderedCategories.indexOfFirst { it.id == category.id }
                                            val newIndex = (currentIndex + moveBy).coerceIn(0, orderedCategories.lastIndex)
                                            if (newIndex != currentIndex) {
                                                orderedCategories = orderedCategories.toMutableList().apply {
                                                    add(newIndex, removeAt(currentIndex))
                                                }
                                                dragOffsetPx -= moveBy * rowHeightPx
                                            }
                                        }
                                    }
                                    draggingId = null
                                    dragOffsetPx = 0f
                                    onReorder(orderedCategories.map { it.id })
                                }
                            }
                        }
                ) {
                    Text(
                        category.name,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ItemGrid(
    items: List<ItemEntity>,
    onItemTap: (ItemEntity) -> Unit,
    onItemLongPress: (ItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { item ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .combinedClickable(
                        onClick = { onItemTap(item) },
                        onLongClick = { onItemLongPress(item) }
                    )
                    .heightIn(min = 96.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        item.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                    Text(
                        item.price.asCurrency(),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    // Just the quantity, colored red at/below the warning threshold —
                    // no badges, dots, or icons, and the item is never hidden.
                    val lowStock = item.stockQty <= item.minStockWarning
                    Text(
                        "${item.stockQty}",
                        color = if (lowStock) Color(0xFFB00020) else MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartPanel(
    lines: List<CartLine>,
    totals: com.possingle.app.viewmodel.CartTotals,
    onQtyChange: (Long, Double) -> Unit,
    onRemove: (Long) -> Unit,
    onCheckout: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var paymentMode by remember { mutableStateOf("CASH") }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
        Text("Current Bill", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(lines, key = { it.item.id }) { line ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(line.item.name, fontWeight = FontWeight.Medium)
                        Text("${line.item.price.asCurrency()} × ${line.qty}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onQtyChange(line.item.id, line.qty - 1) }) {
                        Icon(Icons.Default.Remove, "Decrease")
                    }
                    Text("${line.qty}")
                    IconButton(onClick = { onQtyChange(line.item.id, line.qty + 1) }) {
                        Icon(Icons.Default.Add, "Increase")
                    }
                    IconButton(onClick = { onRemove(line.item.id) }) {
                        Icon(Icons.Default.Close, "Remove")
                    }
                }
                Divider()
            }
        }

        Spacer(Modifier.height(8.dp))
        TotalsRow("Subtotal", totals.subtotal)
        TotalsRow("GST", totals.gstTotal)
        TotalsRow("Grand Total", totals.grandTotal, bold = true)

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("CASH", "CARD", "UPI").forEach { mode ->
                FilterChip(
                    selected = paymentMode == mode,
                    onClick = { paymentMode = mode },
                    label = { Text(mode) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onCheckout(paymentMode) },
            enabled = lines.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Default.Print, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Charge & Print", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TotalsRow(label: String, value: Double, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value.asCurrency(), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun QuickActionSheet(
    item: ItemEntity,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onStock: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onStock) { Text("Adjust Stock") }
                TextButton(onClick = onToggleFavorite) {
                    Text(if (item.isFavorite) "Remove Favorite" else "Mark Favorite")
                }
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
