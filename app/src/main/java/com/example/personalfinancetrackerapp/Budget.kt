package com.example.personalfinancetrackerapp.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets", primaryKeys = ["username"])
data class Budget(
    val username: String,
    val amount: Float = 0f
)