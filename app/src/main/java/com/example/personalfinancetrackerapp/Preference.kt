package com.example.personalfinancetrackerapp.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "preferences")
data class Preference(
    @PrimaryKey val username: String,
    val reminderHour: Int = -1,
    val reminderMinute: Int = -1,
    val currency: String = "$"
)