package com.qedron.gateway

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.qedron.gateway.ui.main.TimeRangePickerDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val colorDrawable = ColorDrawable(ContextCompat.getColor(this,R.color.colorBackground))
        supportActionBar?.setBackgroundDrawable(colorDrawable)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val scope = CoroutineScope(Job() + Dispatchers.Main)
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            findPreference<Preference>("reset")?.setOnPreferenceClickListener {

                context?.let { it1 ->
                    DialogBottomSheet(
                        it1,
                        getString(R.string.dialog_reset_title),
                        getString(R.string.dialog_reset_desc),
                        getString(R.string.dialog_take_me_back),
                        getString(R.string.dialog_reset),
                        true,
                        R.color.colorError,
                        object : DialogBottomSheet.DialogListener {
                            override fun onGo(isGo: Boolean) {
                                if (isGo) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            try {
                                                DatabaseHelperImpl(
                                                    ContactDatabase.getDatabase(it1)
                                                ).deleteAllContacts()
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        it1, "You contact list has been reset.",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        it1,
                                                        "Error deleting contacts. Try again later.",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        }
                                    }

                                }
                            }
                        }).show()
                }
                return@setOnPreferenceClickListener true
            }
            findPreference<Preference>("time_range")?.setOnPreferenceClickListener {
                val dialog = TimeRangePickerDialogFragment()
                dialog.show(parentFragmentManager, "TimeRangePickerDialog")
                true
            }

            findPreference<Preference>("seed")?.setOnPreferenceClickListener {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        context?.let { it1 ->
                            val dbHelper =
                                DatabaseHelperImpl(ContactDatabase.getDatabase(it1))
                            dbHelper.deleteAllContacts()
                            dbHelper.insertAll(GatewayServiceUtil.generateTestContacts(it1))

                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    it1,"Generated 1000 test contacts",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
                true
            }
        }
    }
}