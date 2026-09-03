package com.echocall.app.data.repository

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private var storedVerificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    fun currentUser() = auth.currentUser

    fun sendOtp(
        phoneNumber: String,
        activity: Activity,
        onCodeSent: () -> Unit,
        onVerificationFailed: (String) -> Unit,
        onAutoVerified: (PhoneAuthCredential) -> Unit
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                onAutoVerified(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                onVerificationFailed(e.message ?: "Verification failed")
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                storedVerificationId = verificationId
                resendToken = token
                onCodeSent()
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtp(
        code: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val verificationId = storedVerificationId
        if (verificationId == null) {
            onFailure("Verification session expired, resend OTP")
            return
        }
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        signInWithCredential(credential, onSuccess, onFailure)
    }

    fun signInWithCredential(
        credential: PhoneAuthCredential,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        auth.signInWithCredential(credential)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it.message ?: "Sign in failed") }
    }

    fun signOut() = auth.signOut()
}
