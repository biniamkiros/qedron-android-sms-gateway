package com.qedron.gateway

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class BroadcastBottomSheet(context: Context) : BottomSheetDialog(context) {

    private lateinit var contacts: List<Contact>
    private var count: Int=0
    private var isSent = false
    private var limit = false
    private var frequency = -7
    private var max = 100
    private var top = -1
    private var dialogTitle: TextView? = null
    private var dialogDescription: TextView? = null
    private var dialogContactCount: TextView? = null
    private var dialogLayoutProgress: LinearLayout? = null
    private var dialogProgressMsg: TextView? = null
    private var dialogSuccessMsg : TextView? = null
    private var buttonGo: Button? = null
    private var buttonCancel: Button? = null
    private var dialogInput: TextInputLayout? = null

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private val dbHelper = DatabaseHelperImpl(ContactDatabase.getDatabase(context))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.bottom_sheet_broadcast_dialog)

        setCancelable(false)

        dialogTitle = findViewById(R.id.dialogTitle)
        dialogDescription = findViewById(R.id.dialogDescription)
        dialogContactCount  = findViewById(R.id.dialogContactCount)
        dialogLayoutProgress = findViewById(R.id.dialogLayoutProgress)
        dialogProgressMsg = findViewById(R.id.dialogProgressMsg)

        dialogSuccessMsg = findViewById(R.id.dialogSuccessMsg)

        buttonGo = findViewById(R.id.buttonGo)
        buttonCancel = findViewById(R.id.buttonCancel)
        dialogInput = findViewById(R.id.dialogInput)

        dialogInput?.editText?.doAfterTextChanged {
            if(isSent){
                initBroadCast()
            }
            buttonGo?.visibility = if(it.toString().isNotEmpty()) View.VISIBLE else View.GONE

        }


        buttonGo?.setOnClickListener {
            val message = dialogInput?.editText?.text.toString()
            if(message.isNotEmpty()) broadCast(message)
        }

        buttonCancel?.setOnClickListener {
            dismiss()
        }

        initBroadCast()

    }

    private fun initBroadCast() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val preferences = PreferenceManager.getDefaultSharedPreferences(context)
                count = dbHelper.countContacts()

                limit = preferences.getBoolean("limit", false)
                frequency =  preferences.getString("frequency", "7")!!.toInt() * -1
                max = preferences.getString("max", "100")!!.toInt()
                top = preferences.getString("bulk", "-1")!!.toInt()

                contacts =if(limit) dbHelper.getFreshLimitedContacts(frequency, max, if(top>0) top else count)
                else dbHelper.getFreshContacts(frequency)

                withContext(Dispatchers.Main) {
                    dialogContactCount?.text =
                        context.getString(R.string.contacts_available, contacts.size, count)
                    buttonCancel?.text = context.getString(R.string.abort)
                    dialogSuccessMsg?.visibility = View.GONE
                    dialogSuccessMsg?.text = context.getString(R.string.fetching_result)
                    buttonCancel?.setBackgroundColor(ContextCompat.getColor(context,R.color.colorButton))

                }
            }
        }
    }

    private fun broadCast(message:String) {
        scope.launch {
            buttonGo?.visibility = View.GONE
            dialogInput?.clearFocus()
            dialogLayoutProgress?.visibility = View.VISIBLE
            dialogProgressMsg?.text = context.getString(R.string.preparing_for_broadcast)
            withContext(Dispatchers.IO) {
                var sent = 0
                contacts.forEachIndexed { index, contact ->
                    val success = GatewayServiceUtil.sendMessage(context, contact.phoneNumber, message)
                    if(success) {
                        sent++
                        val now = Calendar.getInstance().time
                        contact.lastContact = now
                        dbHelper.updateContact(contact)
                        dbHelper.insertMessage(Message(contactId = contact.id, message = message, timeStamp = now)
                        )
                    }
                    withContext(Dispatchers.Main) {
                        val progress = "${index + 1}/${contacts.size} sending to ${if (contact.name.isNullOrEmpty()) contact.phoneNumber else "${contact.name} - ${contact.phoneNumber}"}"
                        dialogProgressMsg?.text = progress
                    }
                }
                withContext(Dispatchers.Main){
                    isSent =true
                    buttonCancel?.text = context.getString(R.string.done)
                    buttonCancel?.setBackgroundColor(ContextCompat.getColor(context,R.color.colorSuccess))
                    dialogSuccessMsg?.visibility = View.VISIBLE
                    dialogSuccessMsg?.text = context.getString(
                        R.string.broadcast_sent_to_available_contacts,
                        sent,
                        contacts.size
                    )
                    dialogProgressMsg?.text = context.getString(R.string.preparing_for_broadcast)
                    dialogLayoutProgress?.visibility = View.GONE
                    GatewayServiceUtil.notifyStat(context)
                }
            }
        }
    }

}
