package com.echocall.app.service

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.echocall.app.EchoCallApp
import com.echocall.app.data.repository.AuthRepository
import com.echocall.app.data.repository.FirebaseRepository
import com.echocall.app.ui.IncomingCallActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallFirebaseMessagingService : FirebaseMessagingService() {

    private val authRepository = AuthRepository()
    private val firebaseRepository = FirebaseRepository()

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "incoming_call") return

        val callId = data["callId"] ?: return
        val callerName = data["callerName"] ?: "Unknown"
        val callerNumber = data["callerNumber"] ?: ""
        val callerUid = data["callerUid"] ?: ""

        showIncomingCallScreen(callId, callerName, callerNumber, callerUid)
    }

    override fun onNewToken(token: String) {
        val uid = authRepository.currentUser()?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                firebaseRepository.updateFcmToken(uid, token)
            } catch (_: Exception) {
            }
        }
    }

    private fun showIncomingCallScreen(callId: String, callerName: String, callerNumber: String, callerUid: String) {
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("callId", callId)
            putExtra("callerName", callerName)
            putExtra("callerNumber", callerNumber)
            putExtra("callerUid", callerUid)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, callId.hashCode(), fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, EchoCallApp.CHANNEL_INCOMING_CALL)
            .setContentTitle(callerName)
            .setContentText("Incoming EchoCall")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(callId.hashCode(), notification)
        startActivity(fullScreenIntent)
    }
}
