package com.echocall.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.echocall.app.R
import com.echocall.app.data.local.Contact

class ContactAdapter(
    private val onCallClick: (Contact) -> Unit,
    private val onItemClick: (Contact) -> Unit,
    private val onFavouriteToggle: (Contact) -> Unit
) : ListAdapter<Contact, ContactAdapter.ContactViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvContactName)
        private val tvNumber: TextView = itemView.findViewById(R.id.tvContactNumber)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvContactStatus)

        fun bind(contact: Contact) {
            tvName.text = contact.name
            tvNumber.text = contact.phoneNumber
            val status = if (contact.isAppUser) "On EchoCall" else ""
            val favMark = if (contact.isFavourite) "★ " else ""
            tvStatus.text = favMark + status
            tvStatus.visibility = if (contact.isAppUser || contact.isFavourite) View.VISIBLE else View.GONE

            itemView.setOnClickListener { onItemClick(contact) }
            itemView.setOnLongClickListener {
                onFavouriteToggle(contact)
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Contact>() {
        override fun areItemsTheSame(oldItem: Contact, newItem: Contact) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Contact, newItem: Contact) =
            oldItem == newItem
    }
}
