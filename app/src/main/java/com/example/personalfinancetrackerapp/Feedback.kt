package com.example.personalfinancetrackerapp.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "feedback")
data class Feedback(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val username: String,
    val comment: String
)