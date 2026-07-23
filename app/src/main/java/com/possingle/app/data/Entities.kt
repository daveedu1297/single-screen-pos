package com.possingle.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0
)

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long,
    val price: Double,          // base price, excluding GST
    val gstPercent: Double,     // e.g. 5.0, 12.0, 18.0
    val shortCode: String = "",
    val barcode: String? = null,
    val imagePath: String? = null,
    val stockQty: Double = 0.0,       // opening stock, adjusted from there
    val minStockWarning: Double = 5.0,
    val isVeg: Boolean = true,
    val parcelChargeEnabled: Boolean = false,
    val parcelChargeAmount: Double = 0.0,
    val isFavorite: Boolean = false,
    val isActive: Boolean = true
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val isWalkIn: Boolean = false
)

@Entity(tableName = "stock_adjustments")
data class StockAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val deltaQty: Double,       // positive = added, negative = removed
    val reason: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long?,
    val customerName: String = "Walk-in", // snapshot so the order board can show a name, not just an ID
    val customerPhone: String? = null,
    val subtotal: Double,
    val gstTotal: Double,
    val parcelCharge: Double = 0.0,
    val grandTotal: Double,
    val roundOffAdjustment: Double = 0.0, // difference applied by auto round-off, shown on the bill
    val orderType: String = "Dine-In",
    val paymentMode: String,    // CASH, CARD, UPI, etc.
    val status: String = "OPEN", // OPEN = live on the order board, CLOSED = fulfilled
    val timestampMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "bill_lines")
data class BillLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val itemId: Long,
    val itemName: String,       // denormalized snapshot for receipt reprints
    val qty: Double,
    val unitPrice: Double,
    val gstPercent: Double,
    val lineTotal: Double,
    val served: Boolean = false // ticked off on the live order board
)

/** A bill together with its line items — used to render the live order board. */
data class BillWithLines(
    @androidx.room.Embedded val bill: BillEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "billId")
    val lines: List<BillLineEntity>
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,       // e.g. Rent, Electricity, Salaries, Supplies
    val amount: Double,
    val note: String = "",
    val timestampMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val itemName: String,        // snapshot, same pattern as BillLineEntity
    val qty: Double,
    val unitCost: Double,
    val totalCost: Double,
    val supplierName: String = "",
    val supplierPhone: String? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)
