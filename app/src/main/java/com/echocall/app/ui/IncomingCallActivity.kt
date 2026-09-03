package com.echocall.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.echocall.app.R
import com.echocall.app.data.model.CallSession
import com.echocall.app.data.repository.FirebaseRepository
import kotlinx.coroutines.launch

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var tvCallerName: TextView
    private lateinit var btnAnswer: ImageButton
    private lateinit var btnDecline: ImageButton

    private val firebaseRepository = FirebaseRepository()

    private lateinit var callId: String
    private lateinit var callerName: String
    private lateinit var callerNumber: String
    private lateinit var callerUid: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        callId = intent.getStringExtra("callId") ?: run { finish(); return }
        callerName = intent.getStringExtra("callerName") ?: "Unknown"
        callerNumber = intent.getStringExtra("callerNumber") ?: ""
        callerUid = intent.getStringExtra("callerUid") ?: ""

        tvCallerName = findViewById(R.id.tvCallerName)
        btnAnswer = findViewById(R.id.btnAnswer)
        btnDecline = findViewById(R.id.btnDecline)

        tvCallerName.text = callerName

        btnAnswer.setOnClickListener { answerCall() }
        btnDecline.setOnClickListener { declineCall() }
    }

    private fun answerCall() {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("mode", "incoming")
            putExtra("callId", callId)
            putExtra("callerName", callerName)
            putExtra("callerNumber", callerNumber)
            putExtra("callerUid", callerUid)
        }
        startActivity(intent)
        finish()
    }

    private fun declineCall() {
        lifecycleScope.launch {
            try {
                firebaseRepository.updateCallStatus(callId, CallSession.STATUS_DECLINED)
            } catch (_: Exception) {
            }
            finish()
        }
    }
}
