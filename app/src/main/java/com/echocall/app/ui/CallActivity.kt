package com.echocall.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.echocall.app.R
import com.echocall.app.data.model.CallSession
import com.echocall.app.data.model.IceCandidateModel
import com.echocall.app.data.repository.AuthRepository
import com.echocall.app.data.repository.FirebaseRepository
import com.echocall.app.webrtc.WebRtcClient
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

class CallActivity : AppCompatActivity(), WebRtcClient.Listener {

    private lateinit var tvCallerName: TextView
    private lateinit var tvCallStatus: TextView
    private lateinit var btnMic: ImageButton
    private lateinit var btnSpeaker: ImageButton
    private lateinit var btnVideo: ImageButton
    private lateinit var btnMore: ImageButton
    private lateinit var btnKeypad: ImageButton
    private lateinit var btnEndCall: ImageButton

    private lateinit var webRtcClient: WebRtcClient
    private val firebaseRepository = FirebaseRepository()
    private val authRepository = AuthRepository()
    private lateinit var audioManager: AudioManager

    private var callId: String = ""
    private var mode: String = "outgoing"
    private var isMicEnabled = true
    private var isSpeakerOn = true
    private var callConnected = false
    private var secondsElapsed = 0

    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            secondsElapsed++
            val mins = secondsElapsed / 60
            val secs = secondsElapsed % 60
            tvCallStatus.text = String.format("%02d:%02d", mins, secs)
            handler.postDelayed(this, 1000)
        }
    }

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            proceedWithCall()
        } else {
            tvCallStatus.text = "Microphone permission required"
            handler.postDelayed({ finish() }, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        tvCallerName = findViewById(R.id.tvCallerName)
        tvCallStatus = findViewById(R.id.tvCallStatus)
        btnMic = findViewById(R.id.btnMic)
        btnSpeaker = findViewById(R.id.btnSpeaker)
        btnVideo = findViewById(R.id.btnVideo)
        btnMore = findViewById(R.id.btnMore)
        btnKeypad = findViewById(R.id.btnKeypad)
        btnEndCall = findViewById(R.id.btnEndCall)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true

        mode = intent.getStringExtra("mode") ?: "outgoing"
        val displayName = intent.getStringExtra("callerName")
            ?: intent.getStringExtra("calleeName")
            ?: "Unknown"
        tvCallerName.text = displayName

        btnMic.setOnClickListener { toggleMic() }
        btnSpeaker.setOnClickListener { toggleSpeaker() }
        btnVideo.setOnClickListener {
            Toast.makeText(this, "Video calling coming soon", Toast.LENGTH_SHORT).show()
        }
        btnMore.setOnClickListener {
            Toast.makeText(this, "More options coming soon", Toast.LENGTH_SHORT).show()
        }
        btnKeypad.setOnClickListener {
            Toast.makeText(this, "Keypad coming soon", Toast.LENGTH_SHORT).show()
        }
        btnEndCall.setOnClickListener { endCall() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            proceedWithCall()
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun proceedWithCall() {
        webRtcClient = WebRtcClient(this, this)
        webRtcClient.initialize()

        if (mode == "outgoing") {
            startOutgoingCall()
        } else {
            callId = intent.getStringExtra("callId") ?: ""
            startIncomingCall()
        }
    }

    private fun startOutgoingCall() {
        val calleeUid = intent.getStringExtra("calleeUid") ?: return
        val calleeName = intent.getStringExtra("calleeName") ?: ""
        val calleeNumber = intent.getStringExtra("calleeNumber") ?: ""
        val myUid = authRepository.currentUser()?.uid ?: return
        val myNumber = authRepository.currentUser()?.phoneNumber ?: ""

        tvCallStatus.text = "Calling..."

        webRtcClient.createOffer { offerSdp ->
            lifecycleScope.launch {
                try {
                    val session = CallSession(
                        callerUid = myUid,
                        callerName = myNumber,
                        callerNumber = myNumber,
                        calleeUid = calleeUid,
                        calleeNumber = calleeNumber,
                        offerSdp = offerSdp.description,
                        status = CallSession.STATUS_RINGING
                    )
                    callId = firebaseRepository.createCall(session)
                    listenForCallUpdates()
                    listenForRemoteIce(fromCaller = false)
                } catch (e: Exception) {
                    tvCallStatus.text = "Failed to call"
                }
            }
        }
    }

    private fun startIncomingCall() {
        tvCallStatus.text = "Connecting..."
        lifecycleScope.launch {
            try {
                firebaseRepository.listenToCall(callId) { session ->
                    if (session != null && session.offerSdp.isNotEmpty() && !callConnected) {
                        val offer = SessionDescription(SessionDescription.Type.OFFER, session.offerSdp)
                        webRtcClient.setRemoteDescription(offer)
                        webRtcClient.createAnswer { answerSdp ->
                            lifecycleScope.launch {
                                try {
                                    firebaseRepository.setAnswerSdp(callId, answerSdp.description)
                                    firebaseRepository.updateCallStatus(callId, CallSession.STATUS_ACCEPTED)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                    if (session?.status == CallSession.STATUS_ENDED) {
                        finish()
                    }
                }
                listenForRemoteIce(fromCaller = true)
            } catch (_: Exception) {
            }
        }
    }

    private fun listenForCallUpdates() {
        firebaseRepository.listenToCall(callId) { session ->
            if (session == null) return@listenToCall

            if (session.status == CallSession.STATUS_ACCEPTED && session.answerSdp.isNotEmpty() && !callConnected) {
                val answer = SessionDescription(SessionDescription.Type.ANSWER, session.answerSdp)
                webRtcClient.setRemoteDescription(answer)
                callConnected = true
                tvCallStatus.text = "00:00"
                handler.post(timerRunnable)
            }

            if (session.status == CallSession.STATUS_DECLINED) {
                tvCallStatus.text = "Declined"
                handler.postDelayed({ finish() }, 1500)
            }

            if (session.status == CallSession.STATUS_ENDED) {
                finish()
            }
        }
    }

    private fun listenForRemoteIce(fromCaller: Boolean) {
        firebaseRepository.listenToIceCandidates(callId, fromCaller) { model ->
            val candidate = IceCandidate(model.sdpMid, model.sdpMLineIndex, model.candidate)
            webRtcClient.addIceCandidate(candidate)
        }
    }

    override fun onLocalIceCandidate(candidate: IceCandidate) {
        if (callId.isEmpty()) return
        val isCaller = mode == "outgoing"
        val model = IceCandidateModel(
            sdpMid = candidate.sdpMid ?: "",
            sdpMLineIndex = candidate.sdpMLineIndex,
            candidate = candidate.sdp
        )
        firebaseRepository.addIceCandidate(callId, isCaller, model)
    }

    override fun onRemoteAudioTrackReceived(stream: MediaStream) {
        runOnUiThread {
            if (!callConnected) {
                callConnected = true
                tvCallStatus.text = "00:00"
                handler.post(timerRunnable)
            }
        }
    }

    override fun onConnectionStateChanged(state: PeerConnection.PeerConnectionState?) {
        runOnUiThread {
            when (state) {
                PeerConnection.PeerConnectionState.DISCONNECTED,
                PeerConnection.PeerConnectionState.FAILED,
                PeerConnection.PeerConnectionState.CLOSED -> {
                    if (!isFinishing) endCall()
                }
                else -> {}
            }
        }
    }

    private fun toggleMic() {
        isMicEnabled = !isMicEnabled
        webRtcClient.setMicEnabled(isMicEnabled)
        btnMic.alpha = if (isMicEnabled) 1.0f else 0.4f
    }

    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        audioManager.isSpeakerphoneOn = isSpeakerOn
        btnSpeaker.alpha = if (isSpeakerOn) 1.0f else 0.4f
    }

    private fun endCall() {
        if (callId.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    firebaseRepository.updateCallStatus(callId, CallSession.STATUS_ENDED)
                } catch (_: Exception) {
                }
            }
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
        audioManager.mode = AudioManager.MODE_NORMAL
        webRtcClient.close()
    }
}
