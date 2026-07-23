package com.possingle.app.data

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Intentionally does nothing on creation. Per spec, fresh installs must
 * start with a completely empty database — no sample items, categories,
 * customers, or suppliers. All data is entered by the user through the
 * Add Item / Add Category / Add Customer flows.
 */
class SeedDataCallback(private val context: Context) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // No-op by design.
    }
}
