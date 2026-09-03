package com.echocall.app.data.repository

import com.echocall.app.data.model.CallSession
import com.echocall.app.data.model.IceCandidateModel
import com.echocall.app.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseRepository {

    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")
    private val callsCollection = db.collection("calls")

    suspend fun saveUser(user: User) {
        usersCollection.document(user.uid).set(user).await()
    }

    suspend fun findUsersByNumbers(normalizedNumbers: List<String>): List<User> {
        if (normalizedNumbers.isEmpty()) return emptyList()
        val results = mutableListOf<User>()
        normalizedNumbers.chunked(10).forEach { chunk ->
            val snapshot = usersCollection
                .whereIn("phoneNumber", chunk)
                .get()
                .await()
            results.addAll(snapshot.toObjects(User::class.java))
        }
        return results
    }

    suspend fun updateFcmToken(uid: String, token: String) {
        usersCollection.document(uid).update("fcmToken", token).await()
    }

    suspend fun createCall(session: CallSession): String {
        val docRef = callsCollection.document()
        session.callId = docRef.id
        docRef.set(session).await()
        return docRef.id
    }

    suspend fun updateCallStatus(callId: String, status: String) {
        callsCollection.document(callId).update("status", status).await()
    }

    suspend fun setAnswerSdp(callId: String, answerSdp: String) {
        callsCollection.document(callId).update("answerSdp", answerSdp).await()
    }

    fun listenToCall(callId: String, onUpdate: (CallSession?) -> Unit) =
        callsCollection.document(callId).addSnapshotListener { snapshot, _ ->
            onUpdate(snapshot?.toObject(CallSession::class.java))
        }

    fun addIceCandidate(callId: String, isCaller: Boolean, candidate: IceCandidateModel) {
        val field = if (isCaller) "callerCandidates" else "calleeCandidates"
        callsCollection.document(callId)
            .collection(field)
            .add(candidate)
    }

    fun listenToIceCandidates(callId: String, fromCaller: Boolean, onCandidate: (IceCandidateModel) -> Unit) =
        callsCollection.document(callId)
            .collection(if (fromCaller) "callerCandidates" else "calleeCandidates")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type.name == "ADDED") {
                        change.document.toObject(IceCandidateModel::class.java)
                            .let(onCandidate)
                    }
                }
            }

    fun getUserRef() = usersCollection
}
