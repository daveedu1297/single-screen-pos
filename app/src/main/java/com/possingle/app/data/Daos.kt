package com.possingle.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE isActive = 1 ORDER BY name ASC")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE isActive = 1 AND categoryId = :categoryId ORDER BY name ASC")
    fun observeByCategory(categoryId: Long): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE isActive = 1 AND isFavorite = 1 ORDER BY name ASC")
    fun observeFavorites(): Flow<List<ItemEntity>>

    @Query("""
        SELECT * FROM items 
        WHERE isActive = 1 AND (name LIKE '%' || :query || '%' OR barcode = :query)
        ORDER BY name ASC
    """)
    fun search(query: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): ItemEntity?

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): ItemEntity?

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Query("UPDATE items SET stockQty = stockQty + :delta WHERE id = :itemId")
    suspend fun adjustStock(itemId: Long, delta: Double)

    @Query("UPDATE items SET isActive = 0 WHERE id = :itemId")
    suspend fun softDelete(itemId: Long)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY isWalkIn DESC, name ASC")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<CustomerEntity>>

    @Insert
    suspend fun insert(customer: CustomerEntity): Long
}

@Dao
interface StockAdjustmentDao {
    @Insert
    suspend fun insert(adjustment: StockAdjustmentEntity): Long

    @Query("SELECT * FROM stock_adjustments WHERE itemId = :itemId ORDER BY timestampMillis DESC")
    fun observeForItem(itemId: Long): Flow<List<StockAdjustmentEntity>>
}

@Dao
interface BillDao {
    @Insert
    suspend fun insertBill(bill: BillEntity): Long

    @Insert
    suspend fun insertLines(lines: List<BillLineEntity>)

    @Query("SELECT * FROM bills WHERE timestampMillis BETWEEN :startMillis AND :endMillis ORDER BY timestampMillis DESC")
    fun observeBillsBetween(startMillis: Long, endMillis: Long): Flow<List<BillEntity>>

    @Query("""
        SELECT paymentMode, SUM(grandTotal) as total, COUNT(*) as count 
        FROM bills 
        WHERE timestampMillis BETWEEN :startMillis AND :endMillis 
        GROUP BY paymentMode
    """)
    fun observePaymentSummary(startMillis: Long, endMillis: Long): Flow<List<PaymentSummaryRow>>

    @Query("SELECT * FROM bill_lines WHERE billId = :billId")
    suspend fun getLinesForBill(billId: Long): List<BillLineEntity>

    @Update
    suspend fun updateBill(bill: BillEntity)

    @Query("DELETE FROM bill_lines WHERE billId = :billId")
    suspend fun deleteLinesForBill(billId: Long)

    // --- Live order board ---
    @Transaction
    @Query("SELECT * FROM bills WHERE status = 'OPEN' ORDER BY timestampMillis ASC")
    fun observeOpenOrders(): Flow<List<BillWithLines>>

    @Query("UPDATE bill_lines SET served = :served WHERE id = :lineId")
    suspend fun setLineServed(lineId: Long, served: Boolean)

    @Query("UPDATE bills SET status = 'CLOSED' WHERE id = :billId")
    suspend fun closeOrder(billId: Long)

    // --- Reports: itemized & category sales ---
    @Query("""
        SELECT bl.itemName as itemName, SUM(bl.qty) as totalQty, SUM(bl.lineTotal) as totalRevenue
        FROM bill_lines bl
        INNER JOIN bills b ON bl.billId = b.id
        WHERE b.timestampMillis BETWEEN :startMillis AND :endMillis
        GROUP BY bl.itemName
        ORDER BY totalRevenue DESC
    """)
    fun observeItemizedSales(startMillis: Long, endMillis: Long): Flow<List<ItemSalesRow>>

    @Query("""
        SELECT c.name as categoryName, SUM(bl.qty) as totalQty, SUM(bl.lineTotal) as totalRevenue
        FROM bill_lines bl
        INNER JOIN bills b ON bl.billId = b.id
        INNER JOIN items i ON bl.itemId = i.id
        INNER JOIN categories c ON i.categoryId = c.id
        WHERE b.timestampMillis BETWEEN :startMillis AND :endMillis
        GROUP BY c.name
        ORDER BY totalRevenue DESC
    """)
    fun observeCategorySales(startMillis: Long, endMillis: Long): Flow<List<CategorySalesRow>>
}

data class PaymentSummaryRow(
    val paymentMode: String,
    val total: Double,
    val count: Int
)

data class ItemSalesRow(
    val itemName: String,
    val totalQty: Double,
    val totalRevenue: Double
)

data class CategorySalesRow(
    val categoryName: String,
    val totalQty: Double,
    val totalRevenue: Double
)

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE timestampMillis BETWEEN :startMillis AND :endMillis ORDER BY timestampMillis DESC")
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<ExpenseEntity>>

    @Query("""
        SELECT category as categoryName, SUM(amount) as total
        FROM expenses
        WHERE timestampMillis BETWEEN :startMillis AND :endMillis
        GROUP BY category
        ORDER BY total DESC
    """)
    fun observeByCategoryBetween(startMillis: Long, endMillis: Long): Flow<List<ExpenseCategoryRow>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE timestampMillis BETWEEN :startMillis AND :endMillis")
    fun observeTotalBetween(startMillis: Long, endMillis: Long): Flow<Double>
}

data class ExpenseCategoryRow(
    val categoryName: String,
    val total: Double
)

@Dao
interface PurchaseDao {
    @Insert
    suspend fun insert(purchase: PurchaseEntity): Long

    @Query("SELECT * FROM purchases ORDER BY timestampMillis DESC LIMIT 200")
    fun observeRecent(): Flow<List<PurchaseEntity>>

    @Query("SELECT COALESCE(SUM(totalCost), 0.0) FROM purchases WHERE timestampMillis BETWEEN :startMillis AND :endMillis")
    fun observeTotalCostBetween(startMillis: Long, endMillis: Long): Flow<Double>
}
