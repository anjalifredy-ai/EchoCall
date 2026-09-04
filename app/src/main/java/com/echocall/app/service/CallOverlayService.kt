package com.echocall.app.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import com.echocall.app.R

class CallOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isSpeakerOn = false
    private var isMicMuted = false
    private var callStateListener: SimCallStateListener? = null
    private var audioManager: AudioManager? = null

    private var secondsElapsed = 0
    private var isCallConnected = false
    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            secondsElapsed++
            val mins = secondsElapsed / 60
            val secs = secondsElapsed % 60
            overlayView?.findViewById<TextView>(R.id.tvOverlayStatus)?.text =
                String.format("%02d:%02d", mins, secs)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_HIDE) {
            hideOverlay()
            return START_NOT_STICKY
        }

        val callerName = intent?.getStringExtra("callerName") ?: "Unknown"
        val callerNumber = intent?.getStringExtra("callerNumber") ?: ""
        val status = intent?.getStringExtra("status") ?: "Calling..."

        showOverlay(callerName, callerNumber, status)
        startCallStateTracking()
        return START_NOT_STICKY
    }

    private fun startCallStateTracking() {
        if (callStateListener != null) return
        callStateListener = SimCallStateListener(this) { state ->
            handler.post {
                when (state) {
                    "Connected" -> {
                        if (!isCallConnected) {
                            isCallConnected = true
                            secondsElapsed = 0
                            handler.post(timerRunnable)
                        }
                    }
                    "Call ended" -> {
                        handler.removeCallbacks(timerRunnable)
                        hideOverlay()
                    }
                    else -> {
                        if (!isCallConnected) {
                            overlayView?.findViewById<TextView>(R.id.tvOverlayStatus)?.text = state
                        }
                    }
                }
            }
        }
        callStateListener?.start()
    }

    private fun showOverlay(name: String, number: String, status: String) {
        if (overlayView != null) {
            updateOverlay(name, number, status)
            return
        }

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_call, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP

        setupButtons()
        updateOverlay(name, number, status)

        try {
            windowManager?.addView(overlayView, params)
        } catch (_: Exception) {
        }
    }

    private fun setupButtons() {
        val btnEnd = overlayView?.findViewById<ImageButton>(R.id.btnOverlayEnd)
        val btnSpeaker = overlayView?.findViewById<ImageButton>(R.id.btnOverlaySpeaker)
        val btnMute = overlayView?.findViewById<ImageButton>(R.id.btnOverlayMute)
        val btnHide = overlayView?.findViewById<ImageButton>(R.id.btnOverlayHide)

        btnEnd?.setOnClickListener { endSimCall() }

        btnSpeaker?.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            audioManager?.isSpeakerphoneOn = isSpeakerOn
            btnSpeaker.alpha = if (isSpeakerOn) 1.0f else 0.5f
        }

        btnMute?.setOnClickListener {
            isMicMuted = !isMicMuted
            audioManager?.isMicrophoneMute = isMicMuted
            btnMute.alpha = if (isMicMuted) 1.0f else 0.5f
        }

        btnHide?.setOnClickListener { hideOverlay() }
    }

    private fun endSimCall() {
        try {
            val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
            telecomManager.endCall()
        } catch (_: Exception) {
        }
        hideOverlay()
    }

    private fun hideOverlay() {
        handler.removeCallbacks(timerRunnable)
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        overlayView = null
        audioManager?.mode = AudioManager.MODE_NORMAL
        callStateListener?.stop()
        callStateListener = null
        stopSelf()
    }

    private fun updateOverlay(name: String, number: String, status: String) {
        overlayView?.findViewById<TextView>(R.id.tvOverlayName)?.text = name
        overlayView?.findViewById<TextView>(R.id.tvOverlayNumber)?.text = number
        overlayView?.findViewById<TextView>(R.id.tvOverlayStatus)?.text = status
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        overlayView = null
        audioManager?.mode = AudioManager.MODE_NORMAL
        callStateListener?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_HIDE = "com.echocall.app.ACTION_HIDE_OVERLAY"
    }
}
