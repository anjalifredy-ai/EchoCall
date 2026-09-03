package com.echocall.app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.echocall.app.R
import com.echocall.app.data.repository.ContactRepository
import com.echocall.app.util.PhoneNumberUtil
import kotlinx.coroutines.launch

class AddContactActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etNumber: EditText
    private lateinit var btnSave: Button
    private lateinit var contactRepository: ContactRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_contact)

        contactRepository = ContactRepository(this)

        etName = findViewById(R.id.etName)
        etNumber = findViewById(R.id.etNumber)
        btnSave = findViewById(R.id.btnSave)

        btnSave.setOnClickListener { saveContact() }
    }

    private fun saveContact() {
        val name = etName.text.toString().trim()
        val number = etNumber.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "Enter a name", Toast.LENGTH_SHORT).show()
            return
        }

        val normalized = PhoneNumberUtil.normalize(number)
        if (!PhoneNumberUtil.isValid(normalized)) {
            Toast.makeText(this, "Enter a valid phone number", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                contactRepository.addContact(name, number)
                Toast.makeText(this@AddContactActivity, "Contact saved", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@AddContactActivity, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
