package com.qedron.gateway

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Created by Biniam kiros on 1/31/2020.
 */
class DialogBottomSheet
    (context: Context,
     private val title: String,
     private val description: String,
     private val cancelText: String,
     private val goText: String,
     private val isCancelable: Boolean = true,
     private val dialogButtonTint: Int = R.color.colorAccent,
     private val dialogListener: DialogListener
) : BottomSheetDialog(context) {

    interface DialogListener{
        fun onGo(isGo:Boolean)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.bottom_sheet_dialog)

        setCancelable(isCancelable)
        val dialogTitle:TextView? = findViewById(R.id.dialogTitle)
        val dialogDescription:TextView? = findViewById(R.id.dialogDescription)
        val buttonCancel:Button? = findViewById(R.id.buttonCancel)
        val buttonGo:Button? = findViewById(R.id.buttonGo)

        dialogTitle?.text = title
        dialogDescription?.text = description
        buttonCancel?.text = cancelText
        buttonGo?.text = goText
        buttonGo?.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context,dialogButtonTint))

        buttonGo?.setOnClickListener {
            dismiss()
            dialogListener.onGo(true)
        }

        buttonCancel?.setOnClickListener {
            dismiss()
            dialogListener.onGo(false)
        }

        dialogTitle?.visibility = if (title.isBlank()) View.GONE else View.VISIBLE
        dialogDescription?.visibility = if (description.isBlank()) View.GONE else View.VISIBLE
        buttonCancel?.visibility = if (cancelText.isBlank()) View.GONE else View.VISIBLE
        buttonGo?.visibility = if (goText.isBlank()) View.GONE else View.VISIBLE
    }

}