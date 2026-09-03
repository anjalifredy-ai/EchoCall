package com.echocall.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.echocall.app.R
import com.echocall.app.data.local.Contact
import com.echocall.app.data.model.CallSession
import com.echocall.app.data.repository.AuthRepository
import com.echocall.app.data.repository.ContactRepository
import com.echocall.app.data.repository.FirebaseRepository
import com.echocall.app.ui.adapter.ContactAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var rvContacts: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var fabAddContact: FloatingActionButton
    private lateinit var adapter: ContactAdapter

    private lateinit var contactRepository: ContactRepository
    private val firebaseRepository = FirebaseRepository()
    private val authRepository = AuthRepository()
    private var incomingCallListener: ListenerRegistration? = null
    private var pendingCallNumber: String? = null

    private val requestContactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            syncContacts()
        } else {
            Toast.makeText(this, "Contacts permission needed to find your friends", Toast.LENGTH_LONG).show()
        }
    }

    private val requestCallPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingCallNumber?.let { dialSimCall(it) }
        } else {
            Toast.makeText(this, "Call permission needed to dial", Toast.LENGTH_SHORT).show()
        }
        pendingCallNumber = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        contactRepository = ContactRepository(this)

        rvContacts = findViewById(R.id.rvContacts)
        tvEmpty = findViewById(R.id.tvEmpty)
        fabAddContact = findViewById(R.id.fabAddContact)

        adapter = ContactAdapter(
            onCallClick = { contact -> startCall(contact) },
            onItemClick = { contact -> startCall(contact) }
        )
        rvContacts.layoutManager = LinearLayoutManager(this)
        rvContacts.adapter = adapter

        fabAddContact.setOnClickListener {
            startActivity(Intent(this, AddContactActivity::class.java))
        }

        observeContacts()
        updateFcmToken()
        checkContactsPermission()
    }

    override fun onStart() {
        super.onStart()
        startListeningForIncomingCalls()
    }

    override fun onStop() {
        super.onStop()
        incomingCallListener?.remove()
        incomingCallListener = null
    }

    private fun startListeningForIncomingCalls() {
        val myUid = authRepository.currentUser()?.uid ?: return
        incomingCallListener = firebaseRepository.listenForIncomingCalls(myUid) { session ->
            runOnUiThread {
                openIncomingCallScreen(session)
            }
        }
    }

    private fun openIncomingCallScreen(session: CallSession) {
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("callId", session.callId)
            putExtra("callerName", session.callerName)
            putExtra("callerNumber", session.callerNumber)
            putExtra("callerUid", session.callerUid)
        }
        startActivity(intent)
    }

    private fun observeContacts() {
        lifecycleScope.launch {
            contactRepository.getAllContacts().collect { contacts ->
                adapter.submitList(contacts)
                tvEmpty.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun checkContactsPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            syncContacts()
        } else {
            requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun syncContacts() {
        lifecycleScope.launch {
            try {
                val selfNumber = authRepository.currentUser()?.phoneNumber
                contactRepository.importDeviceContacts(selfNumber)
                matchContactsWithAppUsers()
            } catch (_: Exception) {
            }
        }
    }

    private fun matchContactsWithAppUsers() {
        lifecycleScope.launch {
            try {
                var numbers: List<String> = emptyList()
                contactRepository.getAllContacts().collect { list ->
                    numbers = list.map { it.normalizedNumber }
                    return@collect
                }
                if (numbers.isEmpty()) return@launch

                val matchedUsers = firebaseRepository.findUsersByNumbers(numbers)
                val uidByNumber = matchedUsers.associate { it.phoneNumber to it.uid }
                if (uidByNumber.isNotEmpty()) {
                    contactRepository.markAsAppUsers(uidByNumber)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun updateFcmToken() {
        val uid = authRepository.currentUser()?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            lifecycleScope.launch {
                try {
                    firebaseRepository.updateFcmToken(uid, token)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun startCall(contact: Contact) {
        if (contact.isAppUser && contact.appUid != null) {
            val intent = Intent(this, CallActivity::class.java).apply {
                putExtra("mode", "outgoing")
                putExtra("calleeUid", contact.appUid)
                putExtra("calleeName", contact.name)
                putExtra("calleeNumber", contact.normalizedNumber)
            }
            startActivity(intent)
        } else {
            requestSimCall(contact.phoneNumber)
        }
    }

    private fun requestSimCall(phoneNumber: String) {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            dialSimCall(phoneNumber)
        } else {
            pendingCallNumber = phoneNumber
            requestCallPermission.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun dialSimCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to place call", Toast.LENGTH_SHORT).show()
        }
    }
}
