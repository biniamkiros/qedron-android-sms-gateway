package com.qedron.gateway

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.qedron.gateway.ui.main.ContactsFragment

class ContactRecyclerViewAdapter(
    private val context: Context,
    private val listener: ContactsFragment.ContactClickListener
) :
    RecyclerView.Adapter<ContactRecyclerViewAdapter.ContactViewHolder>() {
    private var data: MutableList<ContactWithMessages> = emptyList<ContactWithMessages>().toMutableList()
    private val layoutInflater: LayoutInflater
    init {
        data = ArrayList<ContactWithMessages>()
        setHasStableIds(true)
        layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        return ContactViewHolder(layoutInflater.inflate(R.layout.contact_list_item, parent, false))
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(data[position])
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun getItemId(position: Int): Long {
        return data[position].contact.id
    }

    fun appendData(newData: MutableList<ContactWithMessages>) {
        val pos = data.size
        data.addAll(newData)
        notifyItemRangeInserted(pos, newData.size)
     }

    fun clearData(){
        val size = data.size
        data.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun dataChanged(contact: Contact) {
        val position = data.indexOfFirst { it.contact.id ==  contact.id }
        if (position != RecyclerView.NO_POSITION) {
            data[position].contact = contact
            notifyItemChanged(position)
        }
    }

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val contactContainer:LinearLayout
        private val numberTxt: TextView
        private val nameTxt: TextView
        private val smsStatTxt: TextView
        private val btnBlocked: ImageButton
        private val rankTxt:TextView

        init {
            contactContainer = itemView.findViewById(R.id.contactContainer)
            numberTxt = itemView.findViewById(R.id.numberTxt)
            nameTxt = itemView.findViewById(R.id.nameTxt)
            smsStatTxt = itemView.findViewById(R.id.smsStatTxt)
            rankTxt =itemView.findViewById(R.id.rankTxt)
            btnBlocked = itemView.findViewById(R.id.blockedBtn)
        }

        fun bind(contact: ContactWithMessages?) {
            if (contact != null) {
                val stat = "last contact ${contact.contact.lastContact.formattedTimeElapsed(context, "never")} • ${if (contact.messages.isNotEmpty()) "${contact.messages.size} sms sent" else "fresh"}"
                numberTxt.text = contact.contact.phoneNumber
                rankTxt.text = contact.contact.ranking.formattedNumber("$")
                nameTxt.text =
                    contact.contact.name.ifEmpty { "unknown" }
                smsStatTxt.text = stat

                btnBlocked.setImageDrawable(
                    AppCompatResources.getDrawable(context, if(contact.contact.isTest) R.drawable.ic_robot_24 else if (contact.contact.blocked) R.drawable.ic_ban_tool_24 else R.drawable.ic_active_24))
                contactContainer.setOnClickListener {listener.onContactClicked(contact) }
                btnBlocked.setOnClickListener { listener.onBlockButtonClicked(contact) }

            }
        }

    }

//    fun <T : RecyclerView.ViewHolder> T.listen(event: (position: Int, type: Int) -> Unit): T {
//        itemView.setOnClickListener {
//            event.invoke(getAdapterPosition(), itemViewType)
//        }
//        return this
//    }


    internal inner class ContactDiffCallback(oldContacts: List<ContactWithMessages>, newContacts: List<ContactWithMessages>) :
        DiffUtil.Callback() {
        private val oldContacts: List<ContactWithMessages>
        private val newContacts: List<ContactWithMessages>

        init {
            this.oldContacts = oldContacts
            this.newContacts = newContacts
        }

        override fun getOldListSize(): Int {
            return oldContacts.size
        }

        override fun getNewListSize(): Int {
            return newContacts.size
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldContacts[oldItemPosition].contact.id.equals(newContacts[newItemPosition].contact.id)
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldContacts[oldItemPosition] == newContacts[newItemPosition]
        }
    }
}