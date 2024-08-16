package com.qedron.gateway

import android.content.Context
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.qedron.gateway.ui.main.ContactsViewModel

class ContactBottomSheet(context: Context,
                         var contact:ContactWithMessages,
                         private val listener: ContactDataChangedListener,
                         private val viewModel: ContactsViewModel) : BottomSheetDialog(context){

    private var numberTxt: TextView? = null
    private var nameTxt: TextView? = null
    private var smsStatTxt: TextView? = null
    private var tagTxt: TextView? = null
    private var detailTxt: TextView? = null
    private var btnBlocked: ImageButton? = null
    private var rankTxt:TextView? = null
    private var contactMessages: LinearLayout? = null
    interface ContactDataChangedListener {
        fun onDataChanged(contact: Contact?)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.bottom_sheet_contact_dialog)

        setCancelable(true)

        numberTxt = findViewById(R.id.numberTxt)
        nameTxt = findViewById(R.id.nameTxt)
        smsStatTxt = findViewById(R.id.smsStatTxt)
        tagTxt = findViewById(R.id.tagTxt)
        detailTxt = findViewById(R.id.detailTxt)
        rankTxt = findViewById(R.id.rankTxt)
        btnBlocked = findViewById(R.id.blockedBtn)
        contactMessages = findViewById(R.id.contactMessages)

        initContact(contact)

        viewModel.updateContact.observe(this) {
            if (it != null) {
                contact.contact = it
                listener.onDataChanged(it)
                initContact(contact)
                Toast.makeText(
                    context,
                    "contact is ${if (it.blocked) "blocked" else "active"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    }

    private fun initContact(contact: ContactWithMessages) {
        val stat = "last contact ${
            contact.contact.lastContact.formattedTimeElapsed(
                context,
                "never"
            )
        } • ${if (contact.messages.isNotEmpty()) "${contact.messages.size} sms sent" else "fresh"}"
        numberTxt?.text = contact.contact.phoneNumber
        rankTxt?.text = contact.contact.ranking.formattedNumber("$")
        nameTxt?.text = contact.contact.name.ifEmpty { " unknown " }
        smsStatTxt?.text = stat
        tagTxt?.text = context.getString(R.string.tag, contact.contact.tag)
        detailTxt?.text = contact.contact.details
        btnBlocked?.setImageDrawable(
            AppCompatResources.getDrawable(
                context,
                if (contact.contact.isTest) R.drawable.ic_robot_24 else if (contact.contact.blocked) R.drawable.ic_ban_tool_24 else R.drawable.ic_active_24
            )
        )
        btnBlocked?.setOnClickListener {
            if (contact.contact.isTest) Toast.makeText(
                context,
                "Contact is a testing account ",
                Toast.LENGTH_LONG
            ).show()
            else contact.contact.apply { blocked = !blocked }
            viewModel.updateContact(contact.contact)
        }

        contactMessages?.removeAllViews()
        contact.messages.forEach {
            val textView = TextView(context)
            textView.text = context.getString(
                R.string.message_with_date,
                it.timeStamp.formattedDate(context.getString(R.string.unknown_date)),
                it.message
            )
            textView.setBackgroundResource(R.drawable.message_background) // Set the background drawable
            textView.setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            textView.setTextColor(ContextCompat.getColor(context, R.color.colorTextOnSurface)) // Replace
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 8.dpToPx(), 0, 8.dpToPx()) // Add 8dp margin top and bottom
            textView.layoutParams = params
            contactMessages?.addView(textView)
        }

    }
}

