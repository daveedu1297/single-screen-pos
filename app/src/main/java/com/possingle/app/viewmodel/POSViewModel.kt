package com.possingle.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.possingle.app.data.*
import com.possingle.app.printer.EscPos
import com.possingle.app.printer.PaperWidth
import com.possingle.app.printer.PrinterConnectionType
import com.possingle.app.printer.PrinterManager
import com.possingle.app.util.Gst
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CartLine(
    val item: ItemEntity,
    val qty: Double
) {
    val lineSubtotal get() = Gst.lineSubtotal(item.price, qty)
    val lineGst get() = Gst.gstAmount(item.price, qty, item.gstPercent)
    val lineTotal get() = Gst.lineTotal(item.price, qty, item.gstPercent)
}

data class CartTotals(
    val subtotal: Double = 0.0,
    val gstTotal: Double = 0.0,
    val parcelCharges: Double = 0.0,
    val grandTotal: Double = 0.0
)

data class StoreSettings(
    val storeName: String = "My Store",
    val storeAddress: String = "",
    val gstin: String = "",
    val invoicePrefix: String = "INV",
    val receiptFooter: String = "Thank you, visit again!"
)

enum class AdminSection { ITEMS, REPORTS, STOCK, SETTINGS, USERS, CATEGORIES }

class POSViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val itemDao = db.itemDao()
    private val categoryDao = db.categoryDao()
    private val customerDao = db.customerDao()
    private val stockDao = db.stockAdjustmentDao()
    private val billDao = db.billDao()
    private val expenseDao = db.expenseDao()
    private val purchaseDao = db.purchaseDao()

    private val prefs = application.getSharedPreferences("pos_prefs", android.content.Context.MODE_PRIVATE)

    // --- Manager PIN (persisted; disabled by default — no forced PIN on fresh install) ---
    private val _pinEnabled = MutableStateFlow(prefs.getBoolean("pin_enabled", false))
    val pinEnabled: StateFlow<Boolean> = _pinEnabled.asStateFlow()

    private val _managerPin = MutableStateFlow(prefs.getString("manager_pin", "") ?: "")
    val managerPin: StateFlow<String> = _managerPin.asStateFlow()

    fun setPinEnabled(enabled: Boolean) {
        _pinEnabled.value = enabled
        prefs.edit().putBoolean("pin_enabled", enabled).apply()
    }

    fun setManagerPin(newPin: String) {
        _managerPin.value = newPin
        prefs.edit().putString("manager_pin", newPin).apply()
    }

    /** Always passes when PIN protection is disabled — Settings screens open directly. */
    fun verifyPin(entered: String): Boolean = !_pinEnabled.value || entered == _managerPin.value

    // --- Store settings (persisted; used on printed receipts and exported reports) ---
    private val _storeSettings = MutableStateFlow(
        StoreSettings(
            storeName = prefs.getString("store_name", "My Store") ?: "My Store",
            storeAddress = prefs.getString("store_address", "") ?: "",
            gstin = prefs.getString("store_gstin", "") ?: "",
            invoicePrefix = prefs.getString("invoice_prefix", "INV") ?: "INV",
            receiptFooter = prefs.getString("receipt_footer", "Thank you, visit again!") ?: "Thank you, visit again!"
        )
    )
    val storeSettings: StateFlow<StoreSettings> = _storeSettings.asStateFlow()

    fun saveStoreSettings(settings: StoreSettings) {
        _storeSettings.value = settings
        prefs.edit()
            .putString("store_name", settings.storeName)
            .putString("store_address", settings.storeAddress)
            .putString("store_gstin", settings.gstin)
            .putString("invoice_prefix", settings.invoicePrefix)
            .putString("receipt_footer", settings.receiptFooter)
            .apply()
    }

    // --- Printer (shared across the Printer setup dialog and checkout printing) ---
    val printerManager = PrinterManager(application)

    private val _printerConnectionType = MutableStateFlow(
        PrinterConnectionType.valueOf(prefs.getString("printer_type", "BLUETOOTH") ?: "BLUETOOTH")
    )
    val printerConnectionType: StateFlow<PrinterConnectionType> = _printerConnectionType.asStateFlow()

    private val _paperWidth = MutableStateFlow(
        PaperWidth.valueOf(prefs.getString("paper_width", "MM_58") ?: "MM_58")
    )
    val paperWidth: StateFlow<PaperWidth> = _paperWidth.asStateFlow()

    private val _lanPrinterIp = MutableStateFlow(prefs.getString("lan_ip", "") ?: "")
    val lanPrinterIp: StateFlow<String> = _lanPrinterIp.asStateFlow()

    fun setPrinterConnectionType(type: PrinterConnectionType) {
        _printerConnectionType.value = type
        prefs.edit().putString("printer_type", type.name).apply()
    }

    fun setPaperWidth(width: PaperWidth) {
        _paperWidth.value = width
        prefs.edit().putString("paper_width", width.name).apply()
    }

    fun setLanPrinterIp(ip: String) {
        _lanPrinterIp.value = ip
        prefs.edit().putString("lan_ip", ip).apply()
    }

    // --- Bill round-off (Settings > Billing) ---
    private val _roundOffMode = MutableStateFlow(
        com.possingle.app.util.RoundOffMode.valueOf(prefs.getString("round_off_mode", "NEAREST") ?: "NEAREST")
    )
    val roundOffMode: StateFlow<com.possingle.app.util.RoundOffMode> = _roundOffMode.asStateFlow()

    fun setRoundOffMode(mode: com.possingle.app.util.RoundOffMode) {
        _roundOffMode.value = mode
        prefs.edit().putString("round_off_mode", mode.name).apply()
    }

    // --- Order Types (Dine-In / Parcel / Self Service) ---
    private val _dineInEnabled = MutableStateFlow(prefs.getBoolean("order_type_dinein", true))
    val dineInEnabled: StateFlow<Boolean> = _dineInEnabled.asStateFlow()
    private val _parcelEnabled = MutableStateFlow(prefs.getBoolean("order_type_parcel", true))
    val parcelEnabled: StateFlow<Boolean> = _parcelEnabled.asStateFlow()
    private val _selfServiceEnabled = MutableStateFlow(prefs.getBoolean("order_type_selfservice", true))
    val selfServiceEnabled: StateFlow<Boolean> = _selfServiceEnabled.asStateFlow()

    fun setOrderTypeEnabled(type: String, enabled: Boolean) {
        when (type) {
            "Dine-In" -> { _dineInEnabled.value = enabled; prefs.edit().putBoolean("order_type_dinein", enabled).apply() }
            "Parcel" -> { _parcelEnabled.value = enabled; prefs.edit().putBoolean("order_type_parcel", enabled).apply() }
            "Self Service" -> { _selfServiceEnabled.value = enabled; prefs.edit().putBoolean("order_type_selfservice", enabled).apply() }
        }
        // If the currently selected order type just got disabled, fall back to Dine-In.
        if (!enabled && _selectedOrderType.value == type) _selectedOrderType.value = "Dine-In"
    }

    private val _selectedOrderType = MutableStateFlow("Dine-In")
    val selectedOrderType: StateFlow<String> = _selectedOrderType.asStateFlow()
    fun selectOrderType(type: String) { _selectedOrderType.value = type }

    // --- Catalog ---
    val categories: StateFlow<List<CategoryEntity>> =
        categoryDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _shortCodeQuery = MutableStateFlow("")
    val shortCodeQuery: StateFlow<String> = _shortCodeQuery.asStateFlow()

    private val _showFavoritesOnly = MutableStateFlow(false)

    // Unfiltered — used by admin dialogs (Items, Stock) so a new item never
    // gets hidden just because the main screen's search/category filter
    // happens to be active when the admin popup is opened.
    val allItems: StateFlow<List<ItemEntity>> =
        itemDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val visibleItems: StateFlow<List<ItemEntity>> = combine(
        _searchQuery, _shortCodeQuery, _selectedCategoryId, _showFavoritesOnly, itemDao.observeAll()
    ) { query, shortCode, categoryId, favoritesOnly, allItems ->
        allItems.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.name.contains(query, ignoreCase = true) ||
                item.barcode == query
            val matchesShortCode = shortCode.isBlank() ||
                item.shortCode.contains(shortCode, ignoreCase = true)
            val matchesCategory = categoryId == null || item.categoryId == categoryId
            val matchesFavorite = !favoritesOnly || item.isFavorite
            matchesQuery && matchesShortCode && matchesCategory && matchesFavorite
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setShortCodeQuery(query: String) { _shortCodeQuery.value = query }
    fun selectCategory(categoryId: Long?) { _selectedCategoryId.value = categoryId }
    fun toggleFavoritesOnly() { _showFavoritesOnly.value = !_showFavoritesOnly.value }

    /** Looks up an item by scanned barcode and adds it straight to the cart (two-tap: scan + confirm). */
    fun onBarcodeScanned(barcode: String, onNotFound: () -> Unit) {
        viewModelScope.launch {
            val item = itemDao.findByBarcode(barcode)
            if (item != null) addToCart(item) else onNotFound()
        }
    }

    // --- Cart ---
    private val _cartLines = MutableStateFlow<List<CartLine>>(emptyList())
    val cartLines: StateFlow<List<CartLine>> = _cartLines.asStateFlow()

    val cartTotals: StateFlow<CartTotals> = combine(_cartLines, _selectedOrderType) { lines, orderType ->
        val subtotal = Gst.round2(lines.sumOf { it.lineSubtotal })
        val gstTotal = Gst.round2(lines.sumOf { it.lineGst })
        val parcelCharges = if (orderType == "Parcel") {
            Gst.round2(lines.sumOf { if (it.item.parcelChargeEnabled) it.item.parcelChargeAmount * it.qty else 0.0 })
        } else 0.0
        CartTotals(subtotal, gstTotal, parcelCharges, Gst.round2(subtotal + gstTotal + parcelCharges))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CartTotals())

    fun addToCart(item: ItemEntity, qty: Double = 1.0) {
        val current = _cartLines.value.toMutableList()
        val idx = current.indexOfFirst { it.item.id == item.id }
        if (idx >= 0) {
            current[idx] = current[idx].copy(qty = current[idx].qty + qty)
        } else {
            current.add(CartLine(item, qty))
        }
        _cartLines.value = current
    }

    fun updateCartQty(itemId: Long, qty: Double) {
        if (qty <= 0.0) {
            removeFromCart(itemId)
            return
        }
        _cartLines.value = _cartLines.value.map {
            if (it.item.id == itemId) it.copy(qty = qty) else it
        }
    }

    fun removeFromCart(itemId: Long) {
        _cartLines.value = _cartLines.value.filterNot { it.item.id == itemId }
    }

    fun clearCart() { _cartLines.value = emptyList() }

    // --- Customer ---
    private val _selectedCustomer = MutableStateFlow<CustomerEntity?>(null)
    val selectedCustomer: StateFlow<CustomerEntity?> = _selectedCustomer.asStateFlow()

    fun selectCustomer(customer: CustomerEntity?) { _selectedCustomer.value = customer }

    /** All saved customers — this is the persistent "phonebook" inside the app. */
    val allCustomers: StateFlow<List<CustomerEntity>> =
        customerDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _customerSearchQuery = MutableStateFlow("")
    val customerSearchResults: StateFlow<List<CustomerEntity>> = _customerSearchQuery.flatMapLatest { query ->
        if (query.isBlank()) allCustomers else customerDao.search(query)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setCustomerSearchQuery(query: String) { _customerSearchQuery.value = query }

    fun addCustomer(name: String, phone: String?, onCreated: (CustomerEntity) -> Unit) {
        viewModelScope.launch {
            val id = customerDao.insert(CustomerEntity(name = name, phone = phone))
            onCreated(CustomerEntity(id = id, name = name, phone = phone))
        }
    }

    // --- Checkout ---
    /**
     * Finalizes the sale: writes the bill + line items (with a snapshot of the
     * customer's name so the order board and printed receipt can show it),
     * decrements stock, prints the receipt on whichever printer is configured,
     * resets the customer back to Walk-in for the next bill, and clears the cart.
     */
    fun checkout(paymentMode: String, onComplete: (billId: Long) -> Unit) {
        val lines = _cartLines.value
        if (lines.isEmpty()) return
        val totals = cartTotals.value
        val customer = _selectedCustomer.value
        val settings = _storeSettings.value
        val orderType = _selectedOrderType.value
        val roundedTotal = Gst.applyRoundOff(totals.grandTotal, _roundOffMode.value)
        val roundOffAdjustment = Gst.round2(roundedTotal - totals.grandTotal)
        viewModelScope.launch {
            val billId = billDao.insertBill(
                BillEntity(
                    customerId = customer?.id,
                    customerName = customer?.name ?: "Walk-in",
                    customerPhone = customer?.phone,
                    subtotal = totals.subtotal,
                    gstTotal = totals.gstTotal,
                    parcelCharge = totals.parcelCharges,
                    grandTotal = roundedTotal,
                    roundOffAdjustment = roundOffAdjustment,
                    orderType = orderType,
                    paymentMode = paymentMode
                )
            )
            billDao.insertLines(lines.map {
                BillLineEntity(
                    billId = billId,
                    itemId = it.item.id,
                    itemName = it.item.name,
                    qty = it.qty,
                    unitPrice = it.item.price,
                    gstPercent = it.item.gstPercent,
                    lineTotal = it.lineTotal
                )
            })
            lines.forEach { itemDao.adjustStock(it.item.id, -it.qty) }

            // Print the receipt with the store's real settings + this bill's customer info.
            val receiptBytes = EscPos.buildReceipt(
                storeName = settings.storeName,
                storeAddress = settings.storeAddress,
                gstin = settings.gstin.ifBlank { null },
                billNumber = "${settings.invoicePrefix}-$billId",
                lines = lines.map { Triple(it.item.name, it.qty, it.lineTotal) },
                subtotal = totals.subtotal,
                gstTotal = totals.gstTotal,
                parcelCharge = totals.parcelCharges,
                roundOffAdjustment = roundOffAdjustment,
                grandTotal = roundedTotal,
                footerMessage = settings.receiptFooter,
                paperWidth = _paperWidth.value
            )
            when (_printerConnectionType.value) {
                PrinterConnectionType.BLUETOOTH -> printerManager.printViaBluetooth(receiptBytes)
                PrinterConnectionType.LAN -> {
                    val ip = _lanPrinterIp.value
                    if (ip.isNotBlank()) printerManager.printViaLan(ip, bytes = receiptBytes)
                }
                PrinterConnectionType.USB -> printerManager.printViaUsb(receiptBytes)
            }

            clearCart()
            selectCustomer(null) // reset to Walk-in so the next bill doesn't reuse this customer
            onComplete(billId)
        }
    }

    // --- Item management (admin) ---
    fun saveItem(item: ItemEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            if (item.id == 0L) itemDao.insert(item) else itemDao.update(item)
            onDone()
        }
    }

    fun deleteItem(itemId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            itemDao.softDelete(itemId)
            onDone()
        }
    }

    fun toggleFavorite(item: ItemEntity) {
        viewModelScope.launch { itemDao.update(item.copy(isFavorite = !item.isFavorite)) }
    }

    fun adjustStock(itemId: Long, delta: Double, reason: String, onDone: () -> Unit) {
        viewModelScope.launch {
            itemDao.adjustStock(itemId, delta)
            stockDao.insert(StockAdjustmentEntity(itemId = itemId, deltaQty = delta, reason = reason))
            onDone()
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            categoryDao.insert(CategoryEntity(name = name, sortOrder = categories.value.size))
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch { categoryDao.delete(category) }
    }

    /** Persists a new drag-and-drop display order for the billing screen's category rail. Doesn't touch category data itself, just sortOrder. */
    fun reorderCategories(orderedIds: List<Long>) {
        viewModelScope.launch {
            val current = categories.value
            orderedIds.forEachIndexed { index, id ->
                val cat = current.find { it.id == id } ?: return@forEachIndexed
                if (cat.sortOrder != index) categoryDao.update(cat.copy(sortOrder = index))
            }
        }
    }

    // --- Live order board ---
    val openOrders: StateFlow<List<BillWithLines>> =
        billDao.observeOpenOrders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setLineServed(lineId: Long, served: Boolean) {
        viewModelScope.launch { billDao.setLineServed(lineId, served) }
    }

    fun closeOrder(billId: Long) {
        viewModelScope.launch { billDao.closeOrder(billId) }
    }

    // --- Reports ---
    fun billsForToday(): Flow<List<BillEntity>> {
        val (start, end) = todayRangeMillis()
        return billDao.observeBillsBetween(start, end)
    }

    /** Fetches the line items for one past bill, for the "view bill" detail screen. */
    fun loadBillLines(billId: Long, onLoaded: (List<BillLineEntity>) -> Unit) {
        viewModelScope.launch { onLoaded(billDao.getLinesForBill(billId)) }
    }

    /** Re-sends a past bill to whatever printer is currently configured. */
    fun reprintBill(bill: BillEntity, lines: List<BillLineEntity>) {
        val settings = _storeSettings.value
        viewModelScope.launch {
            val receiptBytes = EscPos.buildReceipt(
                storeName = settings.storeName,
                storeAddress = settings.storeAddress,
                gstin = settings.gstin.ifBlank { null },
                billNumber = "${settings.invoicePrefix}-${bill.id}",
                lines = lines.map { Triple(it.itemName, it.qty, it.lineTotal) },
                subtotal = bill.subtotal,
                gstTotal = bill.gstTotal,
                parcelCharge = bill.parcelCharge,
                roundOffAdjustment = bill.roundOffAdjustment,
                grandTotal = bill.grandTotal,
                footerMessage = settings.receiptFooter,
                paperWidth = _paperWidth.value
            )
            when (_printerConnectionType.value) {
                PrinterConnectionType.BLUETOOTH -> printerManager.printViaBluetooth(receiptBytes)
                PrinterConnectionType.LAN -> {
                    val ip = _lanPrinterIp.value
                    if (ip.isNotBlank()) printerManager.printViaLan(ip, bytes = receiptBytes)
                }
                PrinterConnectionType.USB -> printerManager.printViaUsb(receiptBytes)
            }
        }
    }

    /**
     * Edits a bill after it's been printed: replaces its line items with
     * `newLines` (name/qty/unitPrice/gstPercent per row), corrects stock to
     * match the difference from what was originally sold, recomputes totals
     * (re-applying round-off), updates the saved bill, and reprints it.
     */
    fun editBill(bill: BillEntity, originalLines: List<BillLineEntity>, newLines: List<CartLine>, onDone: () -> Unit) {
        viewModelScope.launch {
            // Give back the stock for everything on the original bill...
            originalLines.forEach { itemDao.adjustStock(it.itemId, it.qty) }
            // ...then take out stock for the new set of items.
            newLines.forEach { itemDao.adjustStock(it.item.id, -it.qty) }

            val subtotal = Gst.round2(newLines.sumOf { it.lineSubtotal })
            val gstTotal = Gst.round2(newLines.sumOf { it.lineGst })
            val preRoundTotal = Gst.round2(subtotal + gstTotal)
            val roundedTotal = Gst.applyRoundOff(preRoundTotal, _roundOffMode.value)
            val roundOffAdjustment = Gst.round2(roundedTotal - preRoundTotal)

            val updatedBill = bill.copy(
                subtotal = subtotal,
                gstTotal = gstTotal,
                grandTotal = roundedTotal,
                roundOffAdjustment = roundOffAdjustment
            )
            billDao.updateBill(updatedBill)
            billDao.deleteLinesForBill(bill.id)
            billDao.insertLines(newLines.map {
                BillLineEntity(
                    billId = bill.id,
                    itemId = it.item.id,
                    itemName = it.item.name,
                    qty = it.qty,
                    unitPrice = it.item.price,
                    gstPercent = it.item.gstPercent,
                    lineTotal = it.lineTotal
                )
            })

            reprintBill(updatedBill, billDao.getLinesForBill(bill.id))
            onDone()
        }
    }

    fun paymentSummaryForToday(): Flow<List<PaymentSummaryRow>> {
        val (start, end) = todayRangeMillis()
        return billDao.observePaymentSummary(start, end)
    }

    fun itemizedSalesForToday(): Flow<List<ItemSalesRow>> {
        val (start, end) = todayRangeMillis()
        return billDao.observeItemizedSales(start, end)
    }

    fun categorySalesForToday(): Flow<List<CategorySalesRow>> {
        val (start, end) = todayRangeMillis()
        return billDao.observeCategorySales(start, end)
    }

    private fun todayRangeMillis(): Pair<Long, Long> {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val end = start + 24L * 60 * 60 * 1000 - 1
        return start to end
    }

    // --- Expense module ---
    fun expensesForToday(): Flow<List<ExpenseEntity>> {
        val (start, end) = todayRangeMillis()
        return expenseDao.observeBetween(start, end)
    }

    fun expensesByCategoryForToday(): Flow<List<ExpenseCategoryRow>> {
        val (start, end) = todayRangeMillis()
        return expenseDao.observeByCategoryBetween(start, end)
    }

    fun expensesTotalForToday(): Flow<Double> {
        val (start, end) = todayRangeMillis()
        return expenseDao.observeTotalBetween(start, end)
    }

    fun purchasesTotalForToday(): Flow<Double> {
        val (start, end) = todayRangeMillis()
        return purchaseDao.observeTotalCostBetween(start, end)
    }

    fun addExpense(category: String, amount: Double, note: String) {
        viewModelScope.launch { expenseDao.insert(ExpenseEntity(category = category, amount = amount, note = note)) }
    }

    fun updateExpense(expense: ExpenseEntity) {
        viewModelScope.launch { expenseDao.update(expense) }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch { expenseDao.delete(expense) }
    }

    // --- Purchase module ---
    val recentPurchases: StateFlow<List<PurchaseEntity>> =
        purchaseDao.observeRecent().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Records a purchase from a supplier and immediately increases that item's stock. */
    fun recordPurchase(item: ItemEntity, qty: Double, unitCost: Double, supplierName: String, supplierPhone: String?) {
        if (qty <= 0.0) return
        viewModelScope.launch {
            purchaseDao.insert(
                PurchaseEntity(
                    itemId = item.id,
                    itemName = item.name,
                    qty = qty,
                    unitCost = unitCost,
                    totalCost = qty * unitCost,
                    supplierName = supplierName,
                    supplierPhone = supplierPhone
                )
            )
            itemDao.adjustStock(item.id, qty)
        }
    }

    // --- Backup & Restore (manual SQLite file copy via Storage Access Framework) ---
    /**
     * Copies the live database file to the Uri the person picked (from a
     * SAF "Create Document" launcher). Checkpoints WAL first so the copy
     * is consistent even though the app keeps running.
     */
    fun backupDatabaseTo(destinationUri: android.net.Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
                val dbFile = getApplication<Application>().getDatabasePath("pos_database.db")
                getApplication<Application>().contentResolver.openOutputStream(destinationUri)?.use { out ->
                    dbFile.inputStream().use { input -> input.copyTo(out) }
                }
                prefs.edit().putLong("last_backup_millis", System.currentTimeMillis()).apply()
                onResult(true, "Backup saved successfully.")
            } catch (e: Exception) {
                onResult(false, "Backup failed: ${e.message}")
            }
        }
    }

    val lastBackupMillis: Long get() = prefs.getLong("last_backup_millis", 0L)

    /**
     * Restores from a previously backed-up file. Overwrites the live
     * database, then kills the process so the next launch opens the
     * restored file cleanly (Room can't safely hot-swap its open connection).
     */
    fun restoreDatabaseFrom(sourceUri: android.net.Uri, onBeforeRestart: () -> Unit) {
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                db.close()
                val dbFile = app.getDatabasePath("pos_database.db")
                app.contentResolver.openInputStream(sourceUri)?.use { input ->
                    java.io.FileOutputStream(dbFile).use { out -> input.copyTo(out) }
                }
                onBeforeRestart()
                kotlinx.coroutines.delay(800)
                android.os.Process.killProcess(android.os.Process.myPid())
            } catch (e: Exception) {
                onBeforeRestart()
            }
        }
    }

    // --- CSV inventory import (Excel: File > Save As > CSV, then import here) ---
    /**
     * Expects a header row: name,category,price,gstPercent,stockQty,barcode
     * Creates any category that doesn't already exist. Skips malformed rows
     * rather than aborting the whole import, and reports counts back.
     */
    fun importInventoryCsv(csvText: String, onResult: (imported: Int, skipped: Int) -> Unit) {
        viewModelScope.launch {
            val lines = csvText.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) { onResult(0, 0); return@launch }
            var imported = 0
            var skipped = 0
            val existingCategories = categories.value.associateBy { it.name.lowercase() }.toMutableMap()
            lines.drop(1).forEach { line -> // skip header row
                try {
                    val cols = line.split(",").map { it.trim() }
                    val name = cols.getOrNull(0).orEmpty()
                    val categoryName = cols.getOrNull(1).orEmpty().ifBlank { "Uncategorized" }
                    val price = cols.getOrNull(2)?.toDoubleOrNull()
                    val gstPercent = cols.getOrNull(3)?.toDoubleOrNull() ?: 0.0
                    val stockQty = cols.getOrNull(4)?.toDoubleOrNull() ?: 0.0
                    val barcode = cols.getOrNull(5)?.ifBlank { null }

                    if (name.isBlank() || price == null) { skipped++; return@forEach }

                    var category = existingCategories[categoryName.lowercase()]
                    if (category == null) {
                        val newId = categoryDao.insert(CategoryEntity(name = categoryName, sortOrder = existingCategories.size))
                        category = CategoryEntity(id = newId, name = categoryName, sortOrder = existingCategories.size)
                        existingCategories[categoryName.lowercase()] = category
                    }

                    itemDao.insert(
                        ItemEntity(
                            name = name,
                            categoryId = category.id,
                            price = price,
                            gstPercent = gstPercent,
                            stockQty = stockQty,
                            barcode = barcode
                        )
                    )
                    imported++
                } catch (e: Exception) {
                    skipped++
                }
            }
            onResult(imported, skipped)
        }
    }

    companion object {
        /** Header + one example row — what the CSV import expects. */
        const val CSV_TEMPLATE = "name,category,price,gstPercent,stockQty,barcode\nSample Tea,Beverages,20.0,5.0,50,1234567890123\n"
    }
}
