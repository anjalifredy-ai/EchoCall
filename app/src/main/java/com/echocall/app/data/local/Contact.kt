package com.echocall.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val isAppUser: Boolean = false,
    val appUid: String? = null
)
