package com.echocall.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import com.echocall.app.service.CallOverlayService
import com.echocall.app.ui.adapter.ContactAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var rvContacts: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var fabAddContact: FloatingActionButton
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var adapter: ContactAdapter

    private lateinit var contactRepository: ContactRepository
    private val firebaseRepository = FirebaseRepository()
    private val authRepository = AuthRepository()
    private var incomingCallListener: ListenerRegistration? = null
    private var pendingCallContact: Contact? = null
    private var contactsJob: Job? = null

    private val requestContactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            syncContacts()
        } else {
            Toast.makeText(this, "Contacts permission needed to find your friends", Toast.LENGTH_LONG).show()
        }
    }

    private val requestCallPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            pendingCallContact?.let { dialSimCallWithOverlay(it) }
        } else {
            Toast.makeText(this, "Call and phone state permission needed", Toast.LENGTH_SHORT).show()
        }
        pendingCallContact = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        contactRepository = ContactRepository(this)

        rvContacts = findViewById(R.id.rvContacts)
        tvEmpty = findViewById(R.id.tvEmpty)
        fabAddContact = findViewById(R.id.fabAddContact)
        bottomNav = findViewById(R.id.bottomNav)

        adapter = ContactAdapter(
            onCallClick = { contact -> startCall(contact) },
            onItemClick = { contact -> startCall(contact) },
            onFavouriteToggle = { contact -> toggleFavourite(contact) }
        )
        rvContacts.layoutManager = LinearLayoutManager(this)
        rvContacts.adapter = adapter

        fabAddContact.setOnClickListener {
            startActivity(Intent(this, AddContactActivity::class.java))
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dial -> switchTab(TAB_DIAL)
                R.id.nav_contacts -> switchTab(TAB_CONTACTS)
                R.id.nav_favourites -> switchTab(TAB_FAVOURITES)
            }
            true
        }

        switchTab(TAB_DIAL)
        updateFcmToken()
        checkContactsPermission()
        requestOverlayPermissionIfNeeded()
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

    private fun requestOverlayPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                Toast.makeText(this, "Allow 'Display over other apps' for call overlay", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
            }
        }
    }

    private fun switchTab(tab: Int) {
        contactsJob?.cancel()

        val flow = when (tab) {
            TAB_FAVOURITES -> contactRepository.getFavouriteContacts()
            else -> contactRepository.getAllContacts()
        }

        contactsJob = lifecycleScope.launch {
            flow.collect { contacts ->
                adapter.submitList(contacts)
                tvEmpty.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
                tvEmpty.text = if (tab == TAB_FAVOURITES) "No favourites yet" else "No contacts yet"
            }
        }
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

    private fun toggleFavourite(contact: Contact) {
        lifecycleScope.launch {
            try {
                contactRepository.toggleFavourite(contact)
            } catch (_: Exception) {
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
            requestSimCall(contact)
        }
    }

    private fun requestSimCall(contact: Contact) {
        val callGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val phoneStateGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (callGranted && phoneStateGranted) {
            dialSimCallWithOverlay(contact)
        } else {
            pendingCallContact = contact
            requestCallPermissions.launch(
                arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
            )
    }

    private fun dialSimCallWithOverlay(contact: Contact) {
        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${contact.phoneNumber}")
            }
            startActivity(callIntent)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                val overlayIntent = Intent(this, CallOverlayService::class.java).apply {
                    putExtra("callerName", contact.name)
                    putExtra("callerNumber", contact.phoneNumber)
                    putExtra("status", "Calling...")
                }
                startService(overlayIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to place call", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val TAB_DIAL = 0
        const val TAB_CONTACTS = 1
        const val TAB_FAVOURITES = 2
    }
}
