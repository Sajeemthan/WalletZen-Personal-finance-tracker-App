package com.example.personalfinancetrackerapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.personalfinancetrackerapp.model.Budget
import com.example.personalfinancetrackerapp.model.Feedback
import com.example.personalfinancetrackerapp.model.Preference
import com.example.personalfinancetrackerapp.model.Transaction
import com.example.personalfinancetrackerapp.model.User

@Database(
    entities = [User::class, Transaction::class, Budget::class, Preference::class, Feedback::class],
    version = 1,
    exportSchema = false
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao

    companion object {
        @Volatile
        private var INSTANCE: FinanceDatabase? = null

        fun getDatabase(context: Context): FinanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FinanceDatabase::class.java,
                    "finance_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}