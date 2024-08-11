package com.qedron.gateway

import android.content.Context
import android.os.Bundle
import android.widget.Button
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout

/**
 * Created by Biniam kiros on 1/31/2020.
 */
class MergeContactsOptionsBottomSheet
    (context: Context,
     private val dialogListener: DialogListener
) : BottomSheetDialog(context) {

    interface DialogListener{
        fun onMergeOptionsSet(merge:Boolean,
                              mOverrideName: Boolean,
                              mOverrideDetail: Boolean,
                              mOverrideRanking: Boolean,
                              mTagCustom: String,
                              mDoForAll: Boolean
        )
    }
    private var overrideName = false
    private var overrideDetail = false
    private var overrideRanking = false
    private var tagCustom = ""
    private var doForAll = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.bottom_sheet_merge_contacts_options)

        setCancelable(false)

        val buttonCancel:Button? = findViewById(R.id.buttonCancel)
        val buttonGo:Button? = findViewById(R.id.buttonGo)
        val tagInput:TextInputLayout? = findViewById(R.id.tagInput)
        val nameSwitch: SwitchMaterial? = findViewById(R.id.nameSwitch)
        val detailSwitch: SwitchMaterial? = findViewById(R.id.detailSwitch)
        val rankSwitch: SwitchMaterial? = findViewById(R.id.rankSwitch)
        val allCheck: MaterialCheckBox? = findViewById(R.id.allCheck)

        tagInput?.editText?.doAfterTextChanged { tagCustom = it.toString() }
        nameSwitch?.setOnCheckedChangeListener { _, isChecked -> overrideName = isChecked }
        detailSwitch?.setOnCheckedChangeListener { _, isChecked -> overrideDetail = isChecked }
        rankSwitch?.setOnCheckedChangeListener { _, isChecked -> overrideRanking = isChecked }
        allCheck?.setOnCheckedChangeListener { _, isChecked -> doForAll = isChecked }

        buttonGo?.setOnClickListener {
            dismiss()
            dialogListener.onMergeOptionsSet(true, overrideName, overrideDetail, overrideRanking, tagCustom, doForAll)
        }

        buttonCancel?.setOnClickListener {
            dismiss()
            dialogListener.onMergeOptionsSet(false, overrideName, overrideDetail, overrideRanking, tagCustom, doForAll)
        }
    }

}