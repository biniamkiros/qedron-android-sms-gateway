package com.qedron.gateway

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.SEND_SMS
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.preference.PreferenceManager
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.messaging.FirebaseMessaging
import com.qedron.gateway.databinding.ActivityMainBinding
import de.raphaelebner.roomdatabasebackup.core.RoomBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*


class MainActivity : ComponentActivity() {
    private val appViewModelStore: ViewModelStore
        get() = (application as App).viewModelStore

    private val viewModel: BroadcastViewModel by lazy {
        ViewModelProvider(
            appViewModelStore,
            ViewModelProvider.AndroidViewModelFactory(application)
        )[BroadcastViewModel::class.java]
    }
    var newSize = 0
    var modifiedSize = 0
    private lateinit var bottomSheetDialog: BroadcastBottomSheet
    private lateinit var dbHelper: DatabaseHelperImpl
    private lateinit var backup: RoomBackup

    // Job and Dispatcher are combined into a CoroutineContext which
    // will be discussed shortly
    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var binding: ActivityMainBinding

    private var pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // The result data contains a URI for the document or directory that
            // the user selected.

            result.data?.data?.also { uri ->
                // Perform operations on the document using its URI.
                importContacts(uri)
            }
        } else {
            Log.d("FilePicker", "Result code: ${result.resultCode}")
            result.data?.let {
                Log.d("FilePicker", "Data: ${it.data}")
            }
        }
    }

    private val permSmsReqLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all {
                it.value
            }
            if (granted) {
                initFirebase()
            } else {
                Toast.makeText(
                    applicationContext,
                    "SMS permission grant failed. Grant from app settings.", Toast.LENGTH_LONG
                ).show()
                finish()
            }
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
                    applicationContext,
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
                    applicationContext,
                    "Storage permission grant failed. Grant from app settings.", Toast.LENGTH_LONG
                ).show()
            }
        }

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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if(viewModel.status.value == BroadcastViewModel.ONGOING) {
            openBroadcastSheet()
        } else {
           // stopBroadcastService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        actionBar?.title = "SMS gateway"
        actionBar?.setHomeButtonEnabled(true)

        backup = RoomBackup(this)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        dbHelper = DatabaseHelperImpl(ContactDatabase.getDatabase(this))

        binding.bottomAppBar.setNavigationOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.bottomAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.import_contact -> {
                    importOptions()
                    true
                }
                else -> false
            }
        }

        binding.broadcastBtn.setOnClickListener {  openBroadcastSheet() }

        if(checkSMSPermission()) {
            initFirebase()
            checkFresh()
        }


        viewModel.status.observe(this) { status ->
            when(status){
                BroadcastViewModel.ONGOING -> startBroadcastService()
                BroadcastViewModel.ABORTED,
                BroadcastViewModel.KILLED,
                BroadcastViewModel.CLEARED,
                BroadcastViewModel.COMPLETED -> stopBroadcastService()
            }
        }

    }

    private fun checkFresh() {
        if(sharedPreferences.getBoolean("fresh", true)){
            val editor = sharedPreferences.edit()
            if(getBackupFile() != null)
                DialogBottomSheet(
                    this,
                    getString(R.string.dialog_restore_title),
                    getString(R.string.dialog_restore_desc),
                    getString(R.string.dialog_restore_cancel),
                    getString(R.string.dialog_restore),
                    false,
                    R.color.colorError,
                    object : DialogBottomSheet.DialogListener {
                        override fun onGo(isGo: Boolean) {
                            if (isGo) {
                                restoreRoom()
                            }
                        }
                    }).show()
            editor.putBoolean("fresh", false)
            editor.apply()
        }
    }

    private fun checkSMSPermission():Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                SEND_SMS
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            return if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    SEND_SMS
                )
            ) {
                permSmsReqLauncher.launch(
                    arrayOf(SEND_SMS),
                )
                false
            } else {
                Toast.makeText(this, "sms permission is required. Grant permission from app settings", Toast.LENGTH_LONG).show()
                false
            }
        } else {
            return true
        }

    }

    private fun checkWriteFilePermission():Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Snackbar.make(
                    findViewById<View>(android.R.id.content),
                    "Device storage permission needed!",
                    Snackbar.LENGTH_INDEFINITE
                ).setAnchorView(findViewById<View>(R.id.broadcastBtn))
                    .setAction("Settings") {
                        try {
                            val intent = Intent(
                                ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
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
        } else if (ContextCompat.checkSelfPermission(
                this,
                 WRITE_EXTERNAL_STORAGE
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            return if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    WRITE_EXTERNAL_STORAGE
                )
            ) {
                permWriteStorageReqLauncher.launch(
                    arrayOf(WRITE_EXTERNAL_STORAGE),
                )
                false
            } else {
                Toast.makeText(this, "storage permission is required. Grant permission from app settings", Toast.LENGTH_LONG).show()
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
                    findViewById<View>(android.R.id.content),
                    "Device storage permission needed!",
                    Snackbar.LENGTH_INDEFINITE
                ).setAnchorView(findViewById<View>(R.id.broadcastBtn))
                    .setAction("Settings") {
                        try {
                            val intent = Intent(
                                ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
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
        } else if (ContextCompat.checkSelfPermission(
                this,
                READ_EXTERNAL_STORAGE
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            return if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    READ_EXTERNAL_STORAGE
                )
            ) {
                permReadStorageReqLauncher.launch(arrayOf(READ_EXTERNAL_STORAGE))
                false
            } else {
                Toast.makeText(this, "storage permission is required. Grant permission from app settings", Toast.LENGTH_LONG).show()
                false
            }
        } else {
            return true
        }

    }

    private fun initFirebase(){
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                binding.gatewayCloudKey.text = task.result
            }
        }
        binding.gatewayLocalKey.text = getKey()
        binding.gatewayLocalEndpoints.text = getAddressList().joinToString("\n")

        binding.gatewayLocalEnable.isChecked = GatewayServiceUtil.isServiceRunning(this)
        binding.gatewayLocalEnableHolder.setOnClickListener {
            val intent = Intent(this, GatewayService::class.java)
            val running = GatewayServiceUtil.isServiceRunning(this)
            if (running) {
                stopService(intent)
            } else {
                ContextCompat.startForegroundService(this, intent)
                Toast.makeText(
                    applicationContext, "Starting local service...",
                    Toast.LENGTH_LONG
                ).show()
            }
            binding.gatewayLocalEnable.isChecked = !running
        }

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?

        binding.gatewayCloudKeyHolder.setOnClickListener {
            clipboard?.setPrimaryClip(ClipData.newPlainText("key", binding.gatewayCloudKey.text))
            Toast.makeText(this, R.string.gateway_copied_toast, Toast.LENGTH_SHORT).show()
        }
        binding.gatewayLocalKeyHolder.setOnClickListener {
            clipboard?.setPrimaryClip(ClipData.newPlainText("key", binding.gatewayLocalKey.text))
            Toast.makeText(this, R.string.gateway_copied_toast, Toast.LENGTH_SHORT).show()
        }
        binding.gatewayLocalEndpointsHolder.setOnClickListener {
            clipboard?.setPrimaryClip(ClipData.newPlainText("key", binding.gatewayLocalEndpoints.text))
            Toast.makeText(this, R.string.gateway_copied_toast, Toast.LENGTH_SHORT).show()
        }

        GatewayServiceUtil.notifyStat(this)

    }

    override fun onResume() {
        super.onResume()
        updateContactCount()
        if(viewModel.status.value == BroadcastViewModel.ONGOING) {
            openBroadcastSheet()
        } else {
           // stopBroadcastService()
        }

    }

    override fun onPause() {
        super.onPause()
        if (this::bottomSheetDialog.isInitialized && bottomSheetDialog.isShowing)
            bottomSheetDialog.dismiss()
    }

    override fun onDestroy() {
        cleanUp()
        super.onDestroy()
    }

    private var overrideName = false
    private var overrideDetail = false
    private var overrideRanking = false
    private var overrideTagCustom = ""
    private var doForAll = false
    @Throws(IOException::class)
    private fun importContacts(uri: Uri) {
        val dialog = AlertDialog.Builder(ContextThemeWrapper(this, R.style.AlertDialogCustom))
            .setCancelable(false)
            .setView(R.layout.progress_dialog).create()

        val mimeType = contentResolver.getType(uri)
        if(mimeType.toString() == "text/comma-separated-values") {
            scope.launch {
                withContext(Dispatchers.IO) {
                    val contentResolver = applicationContext.contentResolver
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val dbHelper =
                            DatabaseHelperImpl(ContactDatabase.getDatabase(this@MainActivity))

                        val rows: List<Map<String, String>> =
                            csvReader().readAllWithHeader(inputStream)
                        overrideName = false
                        overrideDetail = false
                        overrideRanking = false
                        overrideTagCustom = ""
                        doForAll = false
                        insertContact(
                            rows,
                            0,
                            dialog,
                            dbHelper
                        )
                    }
                }
            }
        } else {
            Toast.makeText(this, "only csv files containing column 'phone' are allowed", Toast.LENGTH_SHORT).show()
        }
    }

    var prevIndex = -1
    private suspend fun insertContact(
        rows: List<Map<String, String>>,
        index: Int,
        dialog: AlertDialog,
        dbHelper: DatabaseHelperImpl
    ) {
        if(prevIndex >= index) return
        prevIndex = index // prevent infinite loop
        if (index == 0) {
            withContext(Dispatchers.Main) {
                dialog.show()
            }
        } else if (rows.size <= index) {
            prevIndex = -1
            withContext(Dispatchers.Main) {
                dialog.cancel()
                val text = if (newSize > 0 || modifiedSize > 0)
                    "Imported $newSize and updated $modifiedSize contacts"
                else
                    "Import not successful. Make sure the file contains at least on column named 'phone'"
                Toast.makeText(
                    this@MainActivity, text,
                    Toast.LENGTH_LONG
                ).show()
                updateContactCount()
                newSize = 0
                modifiedSize = 0
            }
            return
        }
        val r = rows[index]
        val name = if (r["name"].isNullOrEmpty()) "" else r["name"]
        val details = if (r["details"].isNullOrEmpty()) "" else r["details"]
        val phone = if (r["phone"].isNullOrEmpty()) "" else r["phone"]
        val tag = if (r["tag"].isNullOrEmpty()) "" else r["tag"]
        val rank = if (r["rank"].isNullOrEmpty()) 0 else r["rank"]?.toInt() ?: 0
        if (phone !== null && phone.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                val update = "processing ${index + 1}/${rows.size} contacts..."
                dialog.findViewById<TextView>(R.id.progress_msg).text = update
            }
            val phoneNumber = "0${phone.toString().takeLast(9).trim()}"
            if (phoneNumber.length > 9 && phoneNumber.isStringNumeric()) {
                val existingUser = dbHelper.getContactByPhone(phoneNumber)
                if (existingUser != null) {
                    if (doForAll) {
                        val mergedName =
                            mergeContactValues(existingUser.name, name, overrideName, "")
                        val mergedDetails =
                            mergeContactValues(existingUser.details, details, overrideDetail, "")
                        val mergedTag =
                            overrideTagCustom.ifEmpty { combineTags(existingUser.tag, tag) }
                        val mergedRank =
                            mergeContactValues(existingUser.ranking, rank, overrideRanking)
                        dbHelper.insertContact(
                            Contact(
                                name = mergedName,
                                phoneNumber = existingUser.phoneNumber,
                                details = mergedDetails,
                                tag = mergedTag,
                                ranking = mergedRank
                            )
                        )
                        modifiedSize++
                        insertContact(
                            rows,
                            index + 1,
                            dialog,
                            dbHelper
                        )
                    } else {
                        withContext(Dispatchers.Main) {
                            MergeContactsOptionsBottomSheet(this@MainActivity,
                                object :
                                    MergeContactsOptionsBottomSheet.DialogListener {
                                    override fun onMergeOptionsSet(
                                        merge: Boolean,
                                        mOverrideName: Boolean,
                                        mOverrideDetail: Boolean,
                                        mOverrideRanking: Boolean,
                                        mTagCustom: String,
                                        mDoForAll: Boolean
                                    ) {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                if (merge) {
                                                    overrideName = mOverrideName
                                                    overrideDetail =
                                                        mOverrideDetail
                                                    overrideRanking =
                                                        mOverrideRanking
                                                    overrideTagCustom =
                                                        mTagCustom
                                                    doForAll = mDoForAll

                                                    val mergedName =
                                                        mergeContactValues(
                                                            existingUser.name,
                                                            name,
                                                            overrideName,
                                                            ""
                                                        )
                                                    val mergedDetails =
                                                        mergeContactValues(
                                                            existingUser.details,
                                                            details,
                                                            overrideDetail,
                                                            ""
                                                        )
                                                    val mergedTag =
                                                        overrideTagCustom.ifEmpty {
                                                            combineTags(
                                                                existingUser.tag,
                                                                tag
                                                            )
                                                        }
                                                    val mergedRank =
                                                        mergeContactValues(
                                                            existingUser.ranking,
                                                            rank,
                                                            overrideRanking
                                                        )
                                                    dbHelper.insertContact(
                                                        Contact(
                                                            name = mergedName,
                                                            phoneNumber = existingUser.phoneNumber,
                                                            details = mergedDetails,
                                                            tag = mergedTag,
                                                            ranking = mergedRank
                                                        )
                                                    )
                                                    modifiedSize++
                                                    insertContact(
                                                        rows,
                                                        index + 1,
                                                        dialog,
                                                        dbHelper
                                                    )

                                                }
                                                else {
                                                    insertContact(
                                                        rows,
                                                        rows.size,
                                                        dialog,
                                                        dbHelper
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }).show()
                        }
                    }
                } else {
                    dbHelper.insertContact(
                        Contact(
                            name = name ?: "",
                            phoneNumber = phoneNumber,
                            lastContact = null,
                            details = details ?: "",
                            tag = tag ?: "untagged",
                            ranking = rank
                        )
                    )
                    newSize++
                    insertContact(
                        rows,
                        index + 1,
                        dialog,
                        dbHelper
                    )
                }
            }
        }
    }

    private fun cleanUp() {
        // Cancel the scope to cancel ongoing coroutines work
        scope.cancel()
    }

    private fun updateContactCount() {
        scope.launch {
            withContext(Dispatchers.IO) {
                with(PreferenceManager.getDefaultSharedPreferences(this@MainActivity)) {
                    val isLive = getBoolean("live", false)
                    val size = dbHelper.countContacts(!isLive)
                    withContext(Dispatchers.Main) {
                        binding.contactsBtn.text = if (size > 0) getString(
                            R.string.contacts,
                            size
                        ) else getString(R.string.no_contacts)
                        binding.contactsBtn.setOnClickListener {
                            startActivity(
                                Intent(
                                    this@MainActivity,
                                    ContactsActivity::class.java
                                )
                            )
                        }
                        binding.modeTxt.text = getString(if(isLive) R.string.live else R.string.test)
                        binding.modeTxt.setTextColor(getColor(if(isLive) R.color.colorLive else R.color.colorTest))
                        binding.modeTxt.background =  AppCompatResources.getDrawable(this@MainActivity,if(isLive)  R.drawable.live_text_background else R.drawable.test_text_background)
                    }
                }
            }
        }
    }

    private fun getKey(): String {
        var key = sharedPreferences.getString(GatewayService.PREFERENCE_KEY, null)
        if (key == null) {
            key = UUID.randomUUID().toString()
            sharedPreferences.edit().putString(GatewayService.PREFERENCE_KEY, key).apply()
        }
        return key
    }

    private fun getAddressList(): List<String> {
        val result = mutableListOf<String>()
        NetworkInterface.getNetworkInterfaces().toList().forEach { networkInterface ->
            networkInterface.inetAddresses.toList().forEach { address ->
                if (!address.isLoopbackAddress && address is Inet4Address) {
                    result.add("http:/${address}:${GatewayService.DEFAULT_PORT}")
                }
            }
        }
        return result
    }

    override fun getPackageName(): String {
        val trace = Thread.currentThread().stackTrace
        for (i in 1 until trace.size) {
            val currentItem = trace[i]
            if (currentItem.methodName == "getPackageName") {
                val nextItem = trace[i + 1]
                if (nextItem.methodName == "onCreate") {
                    return "com.qedron.gateway"
                }
            }
        }
        return super.getPackageName()
    }

    private fun importOptions() {
        DialogBottomSheet(
            this,
            getString(R.string.dialog_import_title),
            getString(R.string.dialog_import_desc),
            getString(R.string.dialog_import_file),
            getString(R.string.dialog_import_restore),
            true,
            R.color.colorError,
            object : DialogBottomSheet.DialogListener {
                override fun onGo(isGo: Boolean) {
                    if (isGo) {
                        if(checkReadFilePermission()) restoreRoom()
                    } else {
                        pickFile()
                    }
                }
            }).show()
    }

    private fun pickFile(){
        val mimetypes = arrayOf(
//            "text/plain",
//            "application/vnd.ms-excel",
            "text/x-csv",
            "*/csv",
            "application/csv",
            "application/x-csv",
            "text/csv",
            "text/comma-separated-values",
            "text/x-comma-separated-values",
            "text/tab-separated-values"
        )
        val intent = Intent(Intent.ACTION_GET_CONTENT ).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimetypes)
        }

        pickFileLauncher.launch(intent)
    }

    private fun openBroadcastSheet() {
        if (this::bottomSheetDialog.isInitialized && bottomSheetDialog.isShowing) {
//            bottomSheetDialog.dismiss()
//            bottomSheetDialog.setOnDismissListener {
//                Handler(Looper.getMainLooper()).postDelayed({
//                    showBroadcastDialog()
//                }, 100) // Delay of 100 milliseconds
//            }
        } else {
            showBroadcastDialog()
        }
    }

    private fun showBroadcastDialog(){
        bottomSheetDialog = BroadcastBottomSheet(this, object : BroadcastBottomSheet.DialogListener {
            override fun onBroadcastComplete(isGo: Boolean) {
                if (isGo) {
                    if (checkWriteFilePermission())
                        backupRoom()
//                    viewModel.abortBroadcast()
                }
            }
        }, viewModel)
        bottomSheetDialog.show()
    }

    private fun stopBroadcastService() {
        if(this.isServiceRunning(BroadcastService::class.java)) stopService(Intent(this, BroadcastService::class.java))
    }

    private fun startBroadcastService(){
        ContextCompat.startForegroundService(this, Intent(this, BroadcastService::class.java))
    }

    private fun getBackupFile():File? {
        try {
            val path =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).path + File.separator + getString(
                    R.string.app_name
                ) + File.separator + "gatewayDB.sqlite3"
            val file = File(path)

            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
                return file
            }
            return file
        } catch (e:Exception){
            Toast.makeText(
                this,
                "Error locating backup file.",
                Toast.LENGTH_LONG
            ).show()
            return null
        }
    }

    private fun backupRoom() {
        return
        try {
            val file = getBackupFile() ?: return
            backup
                .database(ContactDatabase.getDatabase(this))
                .enableLogDebug(true)
                .backupLocation(RoomBackup.BACKUP_FILE_LOCATION_CUSTOM_FILE)
                .backupLocationCustomFile(file)
                .maxFileCount(5)
                .apply {
                    onCompleteListener { success, message, exitCode ->
                    Log.d("ROOM BACKUP", "success: $success, message: $message, exitCode: $exitCode")
//                        Toast.makeText(
//                            context,
//                            "success: $success, message: $message, exitCode: $exitCode",
//                            Toast.LENGTH_LONG
//                        ).show()
                        if (!success) {
                            Toast.makeText(
                                context,
                                "Backup process was not successful.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                .backup()
        } catch (e:Exception){
            Toast.makeText(
                this,
                "Error:+ ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun restoreRoom() {
        return
        try {
            val file = getBackupFile() ?: return
            backup
                .database(ContactDatabase.getDatabase(this))
                .enableLogDebug(true)
                .backupLocation(RoomBackup.BACKUP_FILE_LOCATION_CUSTOM_FILE)
                .backupLocationCustomFile(file)
                .apply {
                    onCompleteListener { success, message, exitCode ->
                        Log.d("ROOM BACKUP", "success: $success, message: $message, exitCode: $exitCode")
                        if (success) restartApp(Intent(context, MainActivity::class.java))
//                        Toast.makeText(
//                            context,
//                            "Restore: success: $success, message: $message, exitCode: $exitCode",
//                            Toast.LENGTH_LONG
//                        ).show()
                    }
                }
                .restore()
        } catch (e:Exception) {
            Toast.makeText(
                this,
                "Error:+ ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun mergeContactValues(old: String?, new: String?, override: Boolean, defaultValue: String): String =
        when {
            !old.isNullOrBlank() && !new.isNullOrBlank() -> if (override) new else old
            !new.isNullOrBlank() -> new
            !old.isNullOrBlank() -> old
            else -> defaultValue
        }

    private fun mergeContactValues(old: Int, new: Int, override: Boolean): Int =
        when {
            old == 0 && new == 0 -> 0
            old != 0 && new != 0 -> if (override) new else old
            else -> if (new != 0) new else old
        }


    private fun combineTags(existingTag: String?, newTag: String?): String {
        return when {
            existingTag.isNullOrEmpty() && newTag.isNullOrEmpty() -> ""  // Both values are empty or null
            existingTag.isNullOrEmpty() -> newTag.orEmpty()  // Only existingTag is empty or null
            newTag.isNullOrEmpty() -> existingTag  // Only newTag is empty or null
            existingTag == newTag -> existingTag  // Both values are the same (or both null)
            else -> "${existingTag}, $newTag"  // Both values are non-empty (or one is null)
        }
    }
}