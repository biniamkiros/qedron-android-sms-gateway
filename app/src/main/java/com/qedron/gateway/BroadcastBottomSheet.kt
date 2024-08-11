package com.qedron.gateway

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.RangeSlider
import com.google.android.material.textfield.TextInputLayout

class BroadcastBottomSheet(context: Context, private val dialogListener: DialogListener, private val viewModel: BroadcastViewModel) : BottomSheetDialog(context){

    private var modeTxt: TextView? = null
    private var dialogTitle: TextView? = null
    private var dialogDescription: TextView? = null
    private var dialogContactCount: TextView? = null
    private var tagsBtn: Button? = null
    private var rangeBtn: Button? = null
    private var dialogLayoutProgress: LinearLayout? = null
    private var dialogProgressMsg: TextView? = null
    private var dialogSuccessMsg : TextView? = null
    private var buttonGo: Button? = null
    private var buttonCancel: Button? = null
    private var dialogInput: TextInputLayout? = null

    interface DialogListener{
        fun onBroadcastComplete(isGo:Boolean)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.bottom_sheet_broadcast_dialog)

        setCancelable(false)

        dialogTitle = findViewById(R.id.dialogTitle)
        dialogDescription = findViewById(R.id.dialogDescription)
        tagsBtn = findViewById(R.id.tagsBtn)
        rangeBtn = findViewById(R.id.rangeBtn)
        dialogContactCount  = findViewById(R.id.dialogContactCount)
        dialogLayoutProgress = findViewById(R.id.dialogLayoutProgress)
        dialogProgressMsg = findViewById(R.id.dialogProgressMsg)
        dialogSuccessMsg = findViewById(R.id.dialogSuccessMsg)
        buttonGo = findViewById(R.id.buttonGo)
        buttonCancel = findViewById(R.id.buttonCancel)
        dialogInput = findViewById(R.id.dialogInput)
        modeTxt = findViewById(R.id.modeTxt)

        handleStatusChange(viewModel.status.value)

        dialogInput?.editText?.setText(viewModel.broadcastMessage)
        dialogInput?.editText?.doAfterTextChanged {
            viewModel.isMessageModified = true
            viewModel.broadcastMessage = it.toString()
        }

        tagsBtn?.setOnClickListener { showTagsMenu() }
        rangeBtn?.setOnClickListener { showRangeMenu() }
        buttonGo?.setOnClickListener { startBroadcast() }
        buttonCancel?.setOnClickListener { handleCancel() }

        viewModel.progress.observe(this) { showBroadcastProgress(it) }
        viewModel.status.observe(this) { handleStatusChange(it) }
        viewModel.error.observe(this){ showBroadcastReady() }

    }

    private fun handleStatusChange(status: String?) {
        when (status) {
            BroadcastViewModel.STARTED -> initiateBroadcast()
            BroadcastViewModel.INITIATED -> showBroadcastReady()
            BroadcastViewModel.ONGOING -> showBroadcastStart()
            BroadcastViewModel.ABORTED,
            BroadcastViewModel.KILLED,
            BroadcastViewModel.CLEARED,
            BroadcastViewModel.COMPLETED -> showBroadcastDone()
        }
    }

    private fun handleCancel(){
        when(viewModel.status.value){
            BroadcastViewModel.ABORTED,
            BroadcastViewModel.COMPLETED,
            BroadcastViewModel.INITIATED -> {
                dismiss()
                viewModel.finishBroadcast()
                dialogListener.onBroadcastComplete(true)
            }
            BroadcastViewModel.ONGOING -> viewModel.abortBroadcast()
        }
    }

    private fun initiateBroadcast() {
        viewModel.initBroadCast()
        viewModel.isMessageModified = false
    }

    private fun startBroadcast() {
        val intent = Intent(context, BroadcastService::class.java)
        val running = GatewayServiceUtil.isBroadcastRunning(context)
        if (!running) {
            ContextCompat.startForegroundService(context, intent)
        }
        showBroadcastInitialised()
        viewModel.startBroadCast()
    }

    private fun showBroadcastReady() {
        if (viewModel.status.value == BroadcastViewModel.ABORTED
            || viewModel.status.value == BroadcastViewModel.COMPLETED)
            return
        val error = viewModel.error.value?:""
        val tags = viewModel.tags
        val selectedTags = viewModel.selectedTags
            dialogContactCount?.text =
                context.getString(R.string.contacts_available, viewModel.contacts.size, viewModel.count)
        tagsBtn?.text = if (viewModel.selectedTags.isEmpty() || tags.sorted() == selectedTags.sorted()) {
            "all tags"
        } else {
            selectedTags.joinToString(", ") { it.ifEmpty { "untagged" } }
                .let {
                    if (it.length > 14) it.take(14) + "..." else it
                }
        }

        val minMax = viewModel.minMaxRanking
        rangeBtn?.text = viewModel.selectedMinMaxRanking.let { if(it.minRanking > minMax.minRanking
            || it.maxRanking < minMax.maxRanking) "$${it.minRanking} - $${it.maxRanking}" else "full range" }

         if(viewModel.isLive) {
             modeTxt?.text = context.getString(R.string.live)
             modeTxt?.setTextColor(context.getColor(R.color.colorLive))
             modeTxt?.background =  AppCompatResources.getDrawable(context,R.drawable.live_text_background)
         } else {
             modeTxt?.text = context.getString(R.string.test)
             modeTxt?.setTextColor(context.getColor(R.color.colorTest))
             modeTxt?.background = AppCompatResources.getDrawable(context,R.drawable.test_text_background)
         }
        buttonCancel?.text = context.getString(R.string.abort)
        dialogSuccessMsg?.visibility = View.GONE
        dialogSuccessMsg?.text = context.getString(R.string.fetching_result)
        buttonCancel?.setBackgroundColor(ContextCompat.getColor(context, R.color.colorButton))
        dialogInput?.editText?.isEnabled = true

        dialogInput?.error = error.ifEmpty { null }
        if(error.isEmpty()
            && viewModel.broadcastMessage.isNotEmpty()
            && viewModel.status.value == BroadcastViewModel.INITIATED) {
            buttonGo?.visibility = View.VISIBLE
        } else {
            buttonGo?.visibility = View.GONE
            dialogLayoutProgress?.visibility = View.GONE
        }
    }

    private fun showBroadcastInitialised() {
        buttonGo?.visibility = View.GONE
        dialogInput?.clearFocus()
        dialogLayoutProgress?.visibility = View.VISIBLE
        dialogProgressMsg?.text = context.getString(R.string.preparing_for_broadcast)
        dialogSuccessMsg?.visibility = View.GONE
        dialogInput?.editText?.isEnabled = true
    }

    private fun showBroadcastStart() {
        dialogInput?.clearFocus()
        dialogInput?.editText?.isEnabled = false
        buttonGo?.visibility = View.GONE
        dialogLayoutProgress?.visibility = View.VISIBLE
        dialogProgressMsg?.text = context.getString(R.string.preparing_for_broadcast)
        dialogSuccessMsg?.visibility = View.GONE
    }

    private fun showBroadcastProgress(progress: String){
        dialogProgressMsg?.text = progress
        dialogLayoutProgress?.visibility = if (viewModel.status.value == BroadcastViewModel.ONGOING) View.VISIBLE else View.GONE
    }

    private fun showBroadcastDone() {
        buttonCancel?.text = context.getString(R.string.done)
        dialogSuccessMsg?.visibility = View.VISIBLE
        dialogSuccessMsg?.text =
                context.getString(
                    R.string.broadcast_sent_to_available_contacts,
                    viewModel.status.value,
                    viewModel.sent,
                    viewModel.contacts.size
                )
        dialogProgressMsg?.text = context.getString(R.string.preparing_for_broadcast)
        dialogLayoutProgress?.visibility = View.GONE
        buttonGo?.visibility = View.GONE
        dialogInput?.editText?.isEnabled = true
        GatewayServiceUtil.notifyStat(context)
    }

    private fun showTagsMenu() {
        if (viewModel.tags.isEmpty()) {
            Toast.makeText(context, "no tags found.", Toast.LENGTH_LONG).show()
        } else {
            val dialogView = layoutInflater.inflate(R.layout.multiselect_popup, null)
            val listView: ListView = dialogView.findViewById(R.id.tagList)
            val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_multiple_choice, viewModel.tags.map { it.ifEmpty { "untagged" } })
            listView.adapter = adapter

            for (i in viewModel.tags.indices) {
                listView.setItemChecked(i, viewModel.selectedTags.contains(viewModel.tags[i]))
            }

            AlertDialog.Builder(context, R.style.AlertDialogCustom).apply {
                setView(dialogView)
                setPositiveButton("OK") { dialog, _ ->
                    val selectedItems = mutableListOf<String>()
                    for (i in viewModel.tags.indices) {
                        if (listView.isItemChecked(i)) {
                            selectedItems.add(viewModel.tags[i])
                        }
                    }
                    with(PreferenceManager.getDefaultSharedPreferences(context).edit()) {
                        putStringSet("tags", selectedItems.filter { it.isNotEmpty() }.toSet())
                        apply()
                    }
                    viewModel.initBroadCast()
                    dialog.dismiss()
                }
                create()
                show()
            }
        }
    }

    private fun showRangeMenu(){
        if (viewModel.minMaxRanking.minRanking < viewModel.minMaxRanking.maxRanking) {
            val dialogView = layoutInflater.inflate(R.layout.dialog_number_range_slider, null)
            val rangeSlider = dialogView.findViewById<RangeSlider>(R.id.rangeSlider)

            try {
                val from = viewModel.minMaxRanking.minRanking.toFloat()
                val to = viewModel.minMaxRanking.maxRanking.toFloat()

                val min = viewModel.selectedMinMaxRanking.minRanking.toFloat()
                val max = viewModel.selectedMinMaxRanking.maxRanking.toFloat()

                if (min >= from && max <= to) {
                    rangeSlider.valueFrom = from
                    rangeSlider.valueTo = to
                    rangeSlider.values = listOf(min, if(max.toInt() ==0) to else max)

                    AlertDialog.Builder(context, R.style.AlertDialogCustom)
                        .setTitle("Select ranking range")
                        .setView(dialogView)
                        .setPositiveButton("OK") { dialog, _ ->
                            with(PreferenceManager.getDefaultSharedPreferences(context).edit()) {
                                putInt("minRank", rangeSlider.values[0].toInt())
                                putInt("maxRank", rangeSlider.values[1].toInt())
                                apply()
                            }
                            viewModel.initBroadCast()
                            dialog.dismiss()
                        }
                        .create()
                        .show()
                } else {
                    Log.e("RangeSlider", "Selected values are out of bounds min:$min >= from:$from max:$max <= to:$to")
                }
            } catch (e: Exception) {
                Log.e("RangeSlider", "Error setting up RangeSlider", e)
            }
        } else {
            Toast.makeText(context, "no ranking difference found.", Toast.LENGTH_LONG).show()
        }
    }
}
