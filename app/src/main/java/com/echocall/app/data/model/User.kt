package com.echocall.app.data.model

data class User(
    val uid: String = "",
    val phoneNumber: String = "",
    val displayName: String = "",
    val fcmToken: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
