package com.echocall.app.data.model

data class CallSession(
    var callId: String = "",
    var callerUid: String = "",
    var callerName: String = "",
    var callerNumber: String = "",
    var calleeUid: String = "",
    var calleeNumber: String = "",
    var offerSdp: String = "",
    var answerSdp: String = "",
    var status: String = STATUS_RINGING,
    var createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_RINGING = "ringing"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_DECLINED = "declined"
        const val STATUS_ENDED = "ended"
        const val STATUS_MISSED = "missed"
    }
}
