package com.example.personalfinancetrackerapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.personalfinancetrackerapp.model.Budget
import com.example.personalfinancetrackerapp.model.Feedback
import com.example.personalfinancetrackerapp.model.Preference
import com.example.personalfinancetrackerapp.model.Transaction
import com.example.personalfinancetrackerapp.model.User

@Dao
interface FinanceDao {
    // User-related queries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUser(username: String): User?

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<User>

    // Transaction-related queries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransaction(id: Int): Transaction?


    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    // Budget-related queries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: Budget)

    @Query("SELECT * FROM budgets WHERE username = :username LIMIT 1")
    suspend fun getBudget(username: String): Budget?

    @Delete
    suspend fun deleteBudget(budget: Budget)

    @Query("DELETE FROM budgets WHERE username = :username")
    suspend fun deleteBudgetByUser(username: String)

    @Update
    suspend fun updateBudget(budget: Budget)

    // Preference-related queries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreference(preference: Preference)

    @Query("SELECT * FROM preferences WHERE username = :username LIMIT 1")
    suspend fun getPreference(username: String): Preference?

    @Delete
    suspend fun deletePreference(preference: Preference)

    @Query("DELETE FROM preferences WHERE username = :username")
    suspend fun deletePreferenceByUser(username: String)

    @Update
    suspend fun updatePreference(preference: Preference)

    // Feedback-related queries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: Feedback)

    @Query("SELECT * FROM feedback")
    suspend fun getAllFeedback(): List<Feedback>

    @Query("SELECT * FROM transactions WHERE LOWER(TRIM(user)) = :username")
    suspend fun getTransactionsByUser(username: String): List<Transaction>

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactions(): List<Transaction>
}