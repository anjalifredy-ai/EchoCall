package com.echocall.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.echocall.app.R
import com.echocall.app.data.model.User
import com.echocall.app.data.repository.AuthRepository
import com.echocall.app.data.repository.FirebaseRepository
import com.echocall.app.util.PhoneNumberUtil
import com.google.firebase.auth.PhoneAuthCredential
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var etPhoneNumber: EditText
    private lateinit var etOtp: EditText
    private lateinit var btnSendOtp: Button
    private lateinit var btnVerifyOtp: Button
    private lateinit var progressBar: ProgressBar

    private val authRepository = AuthRepository()
    private val firebaseRepository = FirebaseRepository()
    private var normalizedPhone: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        etOtp = findViewById(R.id.etOtp)
        btnSendOtp = findViewById(R.id.btnSendOtp)
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp)
        progressBar = findViewById(R.id.progressBar)

        if (authRepository.currentUser() != null) {
            goToMain()
            return
        }

        btnSendOtp.setOnClickListener { sendOtp() }
        btnVerifyOtp.setOnClickListener { verifyOtp() }
    }

    private fun sendOtp() {
        val rawNumber = etPhoneNumber.text.toString().trim()
        normalizedPhone = PhoneNumberUtil.normalize(rawNumber)

        if (!PhoneNumberUtil.isValid(normalizedPhone)) {
            Toast.makeText(this, "Enter a valid phone number", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        authRepository.sendOtp(
            phoneNumber = normalizedPhone,
            activity = this,
            onCodeSent = {
                setLoading(false)
                etOtp.visibility = View.VISIBLE
                btnVerifyOtp.visibility = View.VISIBLE
                Toast.makeText(this, "OTP sent", Toast.LENGTH_SHORT).show()
            },
            onVerificationFailed = { error ->
                setLoading(false)
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            },
            onAutoVerified = { credential ->
                signIn(credential)
            }
        )
    }

    private fun verifyOtp() {
        val code = etOtp.text.toString().trim()
        if (code.length < 4) {
            Toast.makeText(this, "Enter valid OTP", Toast.LENGTH_SHORT).show()
            return
        }
        setLoading(true)
        authRepository.verifyOtp(
            code = code,
            onSuccess = { onAuthSuccess() },
            onFailure = { error ->
                setLoading(false)
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun signIn(credential: PhoneAuthCredential) {
        setLoading(true)
        authRepository.signInWithCredential(
            credential = credential,
            onSuccess = { onAuthSuccess() },
            onFailure = { error ->
                setLoading(false)
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun onAuthSuccess() {
        val uid = authRepository.currentUser()?.uid ?: return
        val user = User(uid = uid, phoneNumber = normalizedPhone, displayName = normalizedPhone)

        lifecycleScope.launch {
            try {
                firebaseRepository.saveUser(user)
            } catch (_: Exception) {
            }
            setLoading(false)
            goToMain()
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnSendOtp.isEnabled = !loading
        btnVerifyOtp.isEnabled = !loading
    }
}
