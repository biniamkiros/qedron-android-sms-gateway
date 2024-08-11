package com.qedron.gateway.ui.main

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import nl.joery.timerangepicker.TimeRangePicker
import com.qedron.gateway.R

class TimeRangePickerDialogFragment : DialogFragment() {

    private lateinit var timeRangePicker:TimeRangePicker
    private lateinit var start_time: TextView
    private lateinit var duration: TextView
    private lateinit var end_time: TextView
    private lateinit var wake_layout: LinearLayout
    private lateinit var bedtime_layout: LinearLayout
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val startHour = sharedPreferences.getInt("start_hour", 7) // Default to 10:00 PM
        val startMinute = sharedPreferences.getInt("start_minute", 0)
        val endHour = sharedPreferences.getInt("end_hour", 20) // Default to 6:30 AM
        val endMinute = sharedPreferences.getInt("end_minute", 30)

        val layout = layoutInflater.inflate(R.layout.time_range_picker_layout, null) as ConstraintLayout

        start_time = layout.findViewById(R.id.start_time)
        duration = layout.findViewById(R.id.duration)
        end_time = layout.findViewById(R.id.end_time)
        wake_layout = layout.findViewById(R.id.wake_layout)
        bedtime_layout = layout.findViewById(R.id.bedtime_layout)

        timeRangePicker = layout.findViewById<TimeRangePicker>(R.id.timeRangePicker).apply {
            startTimeMinutes = startHour * 60 + startMinute
            endTimeMinutes = endHour * 60 + endMinute
            thumbColorAuto = true
        }

        updateTimes()
        updateDuration()

        timeRangePicker.setOnTimeChangeListener(object : TimeRangePicker.OnTimeChangeListener {
            override fun onStartTimeChange(startTime: TimeRangePicker.Time) {
                updateTimes()
            }

            override fun onEndTimeChange(endTime: TimeRangePicker.Time) {
                updateTimes()
            }

            override fun onDurationChange(duration: TimeRangePicker.TimeDuration) {
                updateDuration()
            }
        })

        timeRangePicker.setOnDragChangeListener(object : TimeRangePicker.OnDragChangeListener {
            override fun onDragStart(thumb: TimeRangePicker.Thumb): Boolean {
                if(thumb != TimeRangePicker.Thumb.BOTH) {
                    animate(thumb, true)
                }
                return true
            }

            override fun onDragStop(thumb: TimeRangePicker.Thumb) {
                if(thumb != TimeRangePicker.Thumb.BOTH) {
                    animate(thumb, false)
                }

                Log.d(
                    "TimeRangePicker",
                    "Start time: " + timeRangePicker.startTime
                )
                Log.d(
                    "TimeRangePicker",
                    "End time: " + timeRangePicker.endTime
                )
                Log.d(
                    "TimeRangePicker",
                    "Total duration: " + timeRangePicker.duration
                )
            }
        })

        return AlertDialog.Builder(context, R.style.AlertDialogCustom)
            .setTitle("Set campaign window")
            .setView(layout)
            .setPositiveButton("OK") { _, _ ->
                // Handle OK button click
                val editor = sharedPreferences.edit()
                editor.putInt("start_hour", timeRangePicker.startTime.hour)
                editor.putInt("start_minute", timeRangePicker.startTime.minute)
                editor.putInt("end_hour", timeRangePicker.endTime.hour)
                editor.putInt("end_minute", timeRangePicker.endTime.minute)
                editor.apply()
            }
            .setNegativeButton("Cancel", null)
            .create()

    }

    @SuppressLint("SetTextI18n")
    private fun updateTimes() {
        end_time.text = timeRangePicker.endTime.toString()
        start_time.text = timeRangePicker.startTime.toString()
    }

    private fun updateDuration() {
        duration.text = getString(R.string.duration, timeRangePicker.duration)
    }

    private fun animate(thumb: TimeRangePicker.Thumb, active: Boolean) {
        val activeView = if(thumb == TimeRangePicker.Thumb.START) bedtime_layout else wake_layout
        val inactiveView = if(thumb == TimeRangePicker.Thumb.START) wake_layout else bedtime_layout
        val direction = if(thumb == TimeRangePicker.Thumb.START) 1 else -1

        activeView
            .animate()
            .translationY(if(active) (activeView.measuredHeight / 2f)*direction else 0f)
            .setDuration(300)
            .start()
        inactiveView
            .animate()
            .alpha(if(active) 0f else 1f)
            .setDuration(300)
            .start()
    }
}
