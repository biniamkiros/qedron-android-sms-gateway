package com.qedron.gateway

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import com.qedron.gateway.ui.main.TimeRangePickerDialogFragment
import de.raphaelebner.roomdatabasebackup.core.RoomBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            val settingsFragment = SettingsFragment()
            val scrollValue = intent.getStringExtra("scroll")
            val bundle = Bundle()
            bundle.putString("scroll", scrollValue)
            settingsFragment.arguments = bundle

            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, settingsFragment)
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val colorDrawable = ColorDrawable(ContextCompat.getColor(this, R.color.colorBackground))
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

        private lateinit var backup: RoomBackup

        // Initialize the variable in before activity creation is complete.
        @RequiresApi(Build.VERSION_CODES.R)
        val storageBackupPermissionResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (Environment.isExternalStorageManager()) {
                backupRoom()
            }
        }

        @RequiresApi(Build.VERSION_CODES.R)
        val storageRestorePermissionResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (Environment.isExternalStorageManager()) restoreRoom()
        }

        private val permWriteStorageReqLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                val granted = permissions.entries.all {
                    it.value
                }
                if (granted) {
                    backupRoom()
                } else {
                    Toast.makeText(
                        context,
                        "Storage permission grant failed. Grant from app settings.", Toast.LENGTH_LONG
                    ).show()
                }
            }

        private val permReadStorageReqLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                val granted = permissions.entries.all {
                    it.value
                }
                if (granted) {
                    restoreRoom()
                } else {
                    Toast.makeText(
                        context,
                        "Storage permission grant failed. Grant from app settings.", Toast.LENGTH_LONG
                    ).show()
                }
            }

        private val scope = CoroutineScope(Job() + Dispatchers.Main)
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            arguments?.getString("scroll")?.let { scroll ->
                findPreference<Preference>(scroll)?.let { preference ->
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(1000)
                        scrollToPreference(preference)
                    }
                }
            }

            backup = context?.let { RoomBackup(it) }!!

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
            findPreference<Preference>("backup")?.setOnPreferenceClickListener {
                if (checkWriteFilePermission()) backupRoom()
                true
            }
            findPreference<Preference>("restore")?.setOnPreferenceClickListener {
                if(checkReadFilePermission()) restoreRoom()
                true
            }

        }

        private fun checkWriteFilePermission():Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    Snackbar.make(
                        requireView(),
                        "Device storage permission needed!",
                        Snackbar.LENGTH_INDEFINITE
                    ).setAction("Settings") {
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + BuildConfig.APPLICATION_ID)
                            )
                            storageBackupPermissionResultLauncher.launch(intent)
                        } catch (ex: Exception) {
                            val intent = Intent()
                            intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            startActivity(intent)
                        }
                    }
                        .show()
                    return false
                } else return true
            } else if (context?.let {
                    ContextCompat.checkSelfPermission(
                        it,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                }
                != PackageManager.PERMISSION_GRANTED
            ) {
                return if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        context as Activity,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                ) {
                    permWriteStorageReqLauncher.launch(
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    )
                    false
                } else {
                    Toast.makeText(context, "storage permission is required. Grant permission from app settings", Toast.LENGTH_LONG).show()
                    false
                }
            } else {
                return true
            }
        }

        private fun checkReadFilePermission():Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    Snackbar.make(
                        requireView(),
                        "Device storage permission needed!",
                        Snackbar.LENGTH_INDEFINITE
                    ).setAction("Settings") {
                            try {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:" + BuildConfig.APPLICATION_ID)
                                )
                                storageRestorePermissionResultLauncher.launch(intent)
                            } catch (ex: Exception) {
                                val intent = Intent()
                                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                startActivity(intent)
                            }
                        }
                        .show()

                    return false
                } else return true
            } else if (context?.let {
                    ContextCompat.checkSelfPermission(
                        it,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                }
                != PackageManager.PERMISSION_GRANTED
            ) {
                return if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        context as Activity,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                ) {
                    permReadStorageReqLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                    false
                } else {
                    Toast.makeText(context, "storage permission is required. Grant permission from app settings", Toast.LENGTH_LONG).show()
                    false
                }
            } else {
                return true
            }

        }

        private fun backupRoom() {
            try {
                val file = context?.let { GatewayServiceUtil.getBackupFile(it) } ?: return
                context?.let {
                    backup
                        .database(ContactDatabase.getDatabase(it) )
                        .enableLogDebug(true)
                        .backupLocation(RoomBackup.BACKUP_FILE_LOCATION_CUSTOM_FILE)
                        .backupLocationCustomFile(file)
                        .maxFileCount(5)
                        .apply {
                            onCompleteListener { success, message, exitCode ->
                                Log.d(
                                    "ROOM BACKUP",
                                    "success: $success, message: $message, exitCode: $exitCode"
                                )
                                Toast.makeText(
                                    context,
                                    "Backup process was ${if (success) "successful" else " not successful"}",
                                    Toast.LENGTH_LONG
                                ).show()
                                if (success) restartGatewayApp()
                            }
                        }
                        .backup()
                }
            } catch (e:Exception){
                Toast.makeText(
                    context,
                    "Error:+ ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        private fun restoreRoom() {
            try {
                val file = context?.let { GatewayServiceUtil.getBackupFile(it) } ?: return
                context?.let {
                    backup
                        .database(ContactDatabase.getDatabase(it) )
                        .enableLogDebug(true)
                        .backupLocation(RoomBackup.BACKUP_FILE_LOCATION_CUSTOM_FILE)
                        .backupLocationCustomFile(file)
                        .apply {
                            onCompleteListener { success, message, exitCode ->
                                Log.d(
                                    "ROOM BACKUP",
                                    "success: $success, message: $message, exitCode: $exitCode"
                                )
                                Toast.makeText(
                                    context,
                                    "Restore process was ${if (success) "successful" else " not successful"}",
                                    Toast.LENGTH_LONG
                                ).show()
                                if (success) restartGatewayApp()
                            }
                        }
                        .restore()
                }
            } catch (e:Exception) {
                Toast.makeText(
                    context,
                    "Error:+ ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        private fun restartGatewayApp(){
            val i: Intent = context?.let { it.packageManager.getLaunchIntentForPackage(it.packageName) }!!
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(i)
            ActivityCompat.finishAfterTransition(context as SettingsActivity)
        }

    }
}