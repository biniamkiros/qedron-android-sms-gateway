package com.qedron.gateway

//import com.simplemobiletools.commons.extensions.*
//import com.simplemobiletools.commons.helpers.*
//import com.simplemobiletools.smsmessenger.activities.SimpleActivity
//import com.simplemobiletools.smsmessenger.databinding.ActivityGatewayBinding

import android.Manifest.permission.SEND_SMS
import android.R.id.message
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.qedron.gateway.databinding.ActivityMainBinding
import com.qedron.gateway.ui.theme.GatewayTheme
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*


class MainActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val PERMISSION_REQUEST_SEND_SMS: Int = 999
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GatewayTheme {
                // A surface container using the 'background' color from the theme
//                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
//                    Greeting("Android")
//                }
            }
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

    }

    private fun checkSMSPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                SEND_SMS
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    SEND_SMS
                )
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf<String>(SEND_SMS),
                    Companion.PERMISSION_REQUEST_SEND_SMS
                )
            }
        }
        else {

//            val message = "#ጨረታ Purchasing of Plastic Floor (Stone Plastic Composite (SPC) Work with Material/2016\n" +
//                    "\uD83D\uDCB5 ፕሮፎርማ    Mon Jun 17th, 24 - Mon Jun 17th, 24\n" +
//                    "\n" +
//                    " \uD83D\uDC49 t.me/SeledaGramBot/start?id=gghnለእርስዎ የሚሆኑ ጨረታዎችን ለመፈለግ ጊዜ አጥሮዎታል?\n" +
//                    "በዚህ ጊዜ ቆጣቢ በሆነ መንገድ ይጠቀሙ። የሚያስፈልግዎን ጨረታዎችን ብቻ መርጦ ይላክልዎታል።\n" +
//                    "ተጨማሪ \uD83D\uDC49 t.me/SeledaGramBot"
//            val sms = SmsManager.getDefault()
//            val parts: ArrayList<String> = sms.divideMessage(message)
//            sms.sendMultipartTextMessage(
//                "+251913201724",
//                null,
//                parts,
//                null,
//                null
//                )
        }

    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            Companion.PERMISSION_REQUEST_SEND_SMS -> {
                if (grantResults.size > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    Toast.makeText(
                        applicationContext, "SMS permission granted successfully.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        applicationContext,
                        "SMS permission grant failed", Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
//        setupToolbar(binding.gatewayToolbar, NavigationIcon.Arrow)
        checkSMSPermission()

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
            clipboard?.text = binding.gatewayCloudKey.text
            Toast.makeText(this, R.string.gateway_copied_toast, Toast.LENGTH_SHORT).show()
        }
        binding.gatewayLocalKeyHolder.setOnClickListener {
            clipboard?.text = binding.gatewayLocalKey.text
            Toast.makeText(this, R.string.gateway_copied_toast, Toast.LENGTH_SHORT).show()
        }
        binding.gatewayLocalEndpointsHolder.setOnClickListener {
            clipboard?.text = binding.gatewayLocalEndpoints.text
            Toast.makeText(this, R.string.gateway_copied_toast, Toast.LENGTH_SHORT).show()
        }

//        updateTextColors(binding.gatewayNestedScrollview)
    }

    override fun onDestroy() {
        super.onDestroy()
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

//    override fun getAppIconIDs() = arrayListOf(
//        R.mipmap.ic_launcher,
//    )

//    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

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

}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
            text = "Hello $name!",
            modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    GatewayTheme {
        Greeting("Android")
    }
}