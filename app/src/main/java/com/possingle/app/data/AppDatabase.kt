package com.possingle.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CategoryEntity::class,
        ItemEntity::class,
        CustomerEntity::class,
        StockAdjustmentEntity::class,
        BillEntity::class,
        BillLineEntity::class,
        ExpenseEntity::class,
        PurchaseEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun itemDao(): ItemDao
    abstract fun customerDao(): CustomerDao
    abstract fun stockAdjustmentDao(): StockAdjustmentDao
    abstract fun billDao(): BillDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun purchaseDao(): PurchaseDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_database.db"
                )
                    // Offline-first: everything lives on-device. Wire a WorkManager
                    // sync job here later if you ever add a cloud backend.
                    .addCallback(SeedDataCallback(context))
                    // While the schema is still evolving during development,
                    // wipe and recreate on a version bump instead of crashing.
                    // Replace with real Migration objects before a production
                    // release where you can't afford to lose existing data.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
