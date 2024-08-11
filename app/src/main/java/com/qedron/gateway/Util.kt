package com.qedron.gateway

import android.app.ActivityManager
import android.content.Context
import android.content.res.Resources
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


/**
 * Extension function to simplify setting an afterTextChanged action to EditText components.
 */
fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit): TextWatcher {
    val watcher = object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    }
    this.addTextChangedListener(watcher)
    return watcher

}


fun Date?.formattedTimeElapsed(context: Context, defaultText:String):String {
    if (this == null) return defaultText
    val time = Calendar.getInstance().timeInMillis - this.time
    val years = time.div(1000).div(60).div(60).div(24).div(365)
    val months = time.div(1000).div(60).div(60).div(24).div(30)
    val weeks = time.div(1000).div(60).div(60).div(24).div(7)
    val days = time.div(1000).div(60).div(60).div(24)
    val hours = time.div(1000).div(60).div(60)
    val minutes = time.div(1000).div(60)
    val seconds = time.div(1000).rem(60)
    val duration = when {
        years > 0 -> String.format(context.getString(R.string.years), years)
        months > 0 -> String.format(context.getString(R.string.months), months)
        weeks > 0 -> String.format(context.getString(R.string.weeks), weeks)
        days > 0 -> String.format(context.getString(R.string.day), days)//"%02d".format(hours))
        hours > 0 -> String.format(context.getString(R.string.hours), hours)//"%02d".format(hours))
        minutes > 0 -> String.format(context.getString(R.string.minutes), minutes)//"%02d".format(minutes))
        else -> String.format(context.getString(R.string.seconds), seconds)// "%02d".format(seconds))
    }
    return "$duration በፊት"
}

fun Date?.formattedDate(defaultText:String):String {
    if (this == null) return defaultText
    return  SimpleDateFormat("E, d MMM yy", Locale.getDefault()).format(this.time)
}

fun Int?.formattedNumber(prefix:String = ""):String {
    return prefix + DecimalFormat("#,###").format(this)
}

fun Int.dpToPx(): Int {
    val scale = Resources.getSystem().displayMetrics.density
    return (this * scale + 0.5f).toInt()
}

fun String.isStringNumeric() :Boolean {
    return toIntOrNull() != null
}

@SuppressWarnings("deprecation")
fun Context.isServiceRunning(serviceClass: Class<*>): Boolean {
    val activityManager = this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
        if (serviceClass.name == service.service.className) {
            return true
        }
    }
    return false
}