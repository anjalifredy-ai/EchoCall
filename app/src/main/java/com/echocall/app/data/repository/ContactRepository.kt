package com.echocall.app.data.repository

import android.content.Context
import android.provider.ContactsContract
import com.echocall.app.data.local.AppDatabase
import com.echocall.app.data.local.Contact
import com.echocall.app.util.PhoneNumberUtil
import kotlinx.coroutines.flow.Flow

class ContactRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).contactDao()
    private val appContext = context.applicationContext

    fun getAllContacts(): Flow<List<Contact>> = dao.getAllContacts()

    suspend fun addContact(name: String, rawNumber: String): Long {
        val normalized = PhoneNumberUtil.normalize(rawNumber)
        val contact = Contact(
            name = name,
            phoneNumber = rawNumber,
            normalizedNumber = normalized
        )
        return dao.insertContact(contact)
    }

    suspend fun updateContact(contact: Contact) = dao.updateContact(contact)

    suspend fun deleteContact(contact: Contact) = dao.deleteContact(contact)

    /**
     * Reads device contacts (requires READ_CONTACTS permission already granted)
     * and imports them into local Room DB, skipping self number.
     */
    suspend fun importDeviceContacts(selfNormalizedNumber: String?) {
        val resolver = appContext.contentResolver
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )

        val imported = mutableListOf<Contact>()
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val seen = mutableSetOf<String>()

            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                val rawNumber = it.getString(numberIdx) ?: continue
                val normalized = PhoneNumberUtil.normalize(rawNumber)

                if (!PhoneNumberUtil.isValid(normalized)) continue
                if (normalized == selfNormalizedNumber) continue
                if (!seen.add(normalized)) continue

                imported.add(
                    Contact(
                        name = name,
                        phoneNumber = rawNumber,
                        normalizedNumber = normalized
                    )
                )
            }
        }

        if (imported.isNotEmpty()) {
            dao.insertAll(imported)
        }
    }

    suspend fun markAsAppUsers(uidByNumber: Map<String, String>) {
        uidByNumber.forEach { (number, uid) ->
            val contact = dao.getContactByNumber(number)
            if (contact != null) {
                dao.updateContact(contact.copy(isAppUser = true, appUid = uid))
            }
        }
    }
}
