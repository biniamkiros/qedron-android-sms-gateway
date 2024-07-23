package com.qedron.gateway

import android.Manifest.permission.SEND_SMS
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.google.firebase.messaging.FirebaseMessaging
import com.qedron.gateway.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*


class MainActivity : ComponentActivity() {

    private lateinit var dbHelper: DatabaseHelperImpl

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
        }
    }
    private val permReqLauncher =
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        actionBar?.title = "SMS gateway"
        actionBar?.setHomeButtonEnabled(true)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        dbHelper = DatabaseHelperImpl(ContactDatabase.getDatabase(this))

        binding.bottomAppBar.setNavigationOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.bottomAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.import_contact -> {
                    pickFile()
                    true
                }
                else -> false
            }
        }

        binding.broadcastBtn.setOnClickListener {  openBroadcastSheet() }

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
                permReqLauncher.launch(
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
        if(checkSMSPermission()) initFirebase()
        updateContactCount()
    }

    override fun onDestroy() {
        cleanUp()
        super.onDestroy()
    }

    @Throws(IOException::class)
    private fun importContacts(uri: Uri) {
        val dialog = AlertDialog.Builder(ContextThemeWrapper(this, R.style.AlertDialogCustom))
            .setCancelable(false)
            .setView(R.layout.progress_dialog).create()
        val mimeType = contentResolver.getType(uri)
        if(mimeType.toString() == "text/comma-separated-values") {
            dialog.show()
            scope.launch {
                withContext(Dispatchers.IO) {
                    val contentResolver = applicationContext.contentResolver
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val dbHelper =
                            DatabaseHelperImpl(ContactDatabase.getDatabase(this@MainActivity))
                        var size = 0
                        val rows: List<Map<String, String>> = csvReader().readAllWithHeader(inputStream)
                        val total = rows.size
                        rows.forEachIndexed { index, r ->
                            val name = if(r["name"].isNullOrEmpty()) "" else r["name"]
                            val phone = if(r["phone"].isNullOrEmpty()) "" else r["phone"]
                            if(phone !== null && phone.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    val update = "processing ${index+1}/${total} contacts..."
                                    dialog.findViewById<TextView>(R.id.progress_msg).text = update
                                }
                                val phoneNumber = "0" + phone.toString().takeLast(9)
                                if(phoneNumber.length == 10) {
                                    dbHelper.insertContact(
                                        Contact(
                                            name = name,
                                            phoneNumber = "0" + phone.toString().takeLast(9),
                                            lastContact = null,
                                        )
                                    )
                                    size++
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            dialog.cancel()
                            val text = if(size > 0)
                                "Imported to $size contacts"
                            else
                                "Import not successful. Make sure the file contains at least on column named 'phone'"
                            Toast.makeText(
                                this@MainActivity,text,
                                Toast.LENGTH_LONG
                            ).show()
                            updateContactCount()
                        }
                    }
                }
            }
        } else {
            Toast.makeText(this, "only csv files containing column 'phone' are allowed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cleanUp() {
        // Cancel the scope to cancel ongoing coroutines work
        scope.cancel()
    }

    private fun updateContactCount() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val size = dbHelper.countContacts()
                withContext(Dispatchers.Main) {
                    binding.contactsBtn.text = if(size >0 ) getString(R.string.contacts, size) else getString(R.string.no_contacts)
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
        BroadcastBottomSheet(this).show()
    }
}