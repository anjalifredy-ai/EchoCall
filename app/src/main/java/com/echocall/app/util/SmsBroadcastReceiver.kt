package com.echocall.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status

class SmsBroadcastReceiver : BroadcastReceiver() {

    var otpListener: ((String) -> Unit)? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        if (SmsRetriever.SMS_RETRIEVED_ACTION == intent?.action) {
            val extras = intent.extras
            val status = extras?.get(SmsRetriever.EXTRA_STATUS) as? Status

            when (status?.statusCode) {
                CommonStatusCodes.SUCCESS -> {
                    val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE) ?: return
                    val otp = extractOtp(message)
                    if (otp != null) {
                        otpListener?.invoke(otp)
                    }
                }
                else -> {}
            }
        }
    }

    private fun extractOtp(message: String): String? {
        val regex = Regex("\\b\\d{4,6}\\b")
        return regex.find(message)?.value
    }
}
