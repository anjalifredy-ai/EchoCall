package com.echocall.app.service

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager

class SimCallStateListener(
    private val context: Context,
    private val onStateChanged: (String) -> Unit
) {
    private var telephonyManager: TelephonyManager? = null
    private var legacyListener: PhoneStateListener? = null
    private var modernCallback: TelephonyCallback? = null

    fun start() {
        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    reportState(state)
                }
            }
            modernCallback = callback
            try {
                telephonyManager?.registerTelephonyCallback(context.mainExecutor, callback)
            } catch (_: Exception) {
            }
        } else {
            val listener = object : PhoneStateListener() {
                @Suppress("DEPRECATION")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    reportState(state)
                }
            }
            legacyListener = listener
            try {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            } catch (_: Exception) {
            }
        }
    }

    private fun reportState(state: Int) {
        val label = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> "Ringing..."
            TelephonyManager.CALL_STATE_OFFHOOK -> "Connected"
            TelephonyManager.CALL_STATE_IDLE -> "Call ended"
            else -> "Calling..."
        }
        onStateChanged(label)
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modernCallback?.let {
                try {
                    telephonyManager?.unregisterTelephonyCallback(it)
                } catch (_: Exception) {
                }
            }
        } else {
            legacyListener?.let {
                try {
                    @Suppress("DEPRECATION")
                    telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
                } catch (_: Exception) {
                }
            }
        }
    }
}
