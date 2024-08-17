package com.qedron.gateway

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ceil

class BroadcastViewModel(private val application: Application) : AndroidViewModel(application) {
    companion object{
        const val STARTED = "started"
        const val INITIATED = "initiated"
        const val ONGOING = "ongoing"
        const val ABORTED = "aborted"
        const val FAILED = "failed"
        const val KILLED = "killed"
        const val CLEARED = "cleared"
        const val COMPLETED = "completed"
        const val FINISHED = "finished"
    }

    private val dbHelper = DatabaseHelperImpl(ContactDatabase.getDatabase(application))
    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val smsManager = GatewayServiceUtil.getSmsManager(application)
    private var job: Job? = null
    private var lifeTimeLimit = false
    private var frequency = -7L
    private var maxLifeTimeMessages = 100
    private var carrierLimit = 1000L
    private var top = -1
    private var sendError =false
    private var abortOnError =false
    private var pauseOnError =1000L
    private var genericErrorCount = 0
    private var genericErrorLimit = 0

    var rankings = emptyList<Long>()
    var isMessageModified = false
    var sent = 0
        set(value) {
            field = value
            hasBroadcastErrors()
        }
    var abort =false
        set(value) {
            field = value
            hasBroadcastErrors()
        }
    var completed = false
        set(value) {
            field = value
            hasBroadcastErrors()
        }
    var isLive = false
        set(value) {
            field = value
            hasBroadcastErrors()
        }
    var count: Int=0
        set(value) {
            field = value
            hasBroadcastErrors()
        }
    var tags= emptyList<String>()
        set(value) {
            field = value
            hasBroadcastErrors()
        }
    var selectedTags= emptyList<String>()
        set(value) {
            field = value
            hasBroadcastErrors()
        }
    var minMaxRanking: MinMaxRanking = MinMaxRanking(0,0, Long.MAX_VALUE)
        set(value) {
            field = value
            hasBroadcastErrors()
        }
    var selectedMinMaxRanking: MinMaxRanking = MinMaxRanking(0, 0, 0)
        set(value) {
            field = value
            hasBroadcastErrors()
        }
    var broadcastMessage:String = ""
        set(value) {
            field = value
            hasBroadcastErrors()
            if (status.value != INITIATED) initBroadCast()
        }

    var contacts: List<Contact> = emptyList()

    private val _progress = MutableLiveData<String>()
    val progress: LiveData<String> = _progress

    private val _status = MutableLiveData<String>(STARTED)
    val status: LiveData<String> = _status

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    fun initBroadCast() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                genericErrorCount = 0
                abort = false
                sent = 0
                completed = false
                tags = dbHelper.getAllUniqueTags()
                minMaxRanking = dbHelper.getMinAndMaxRanking()
                isLive = preferences.getBoolean("live", false)
                count = dbHelper.countContacts(!isLive)
                carrierLimit = preferences.getString("delay", "1000")!!.toLong()
                genericErrorLimit = preferences.getString("error_limit", "10")!!.toInt()
                lifeTimeLimit = preferences.getBoolean("limit", false)
                val minRank = preferences.getLong("minRank", minMaxRanking.minRanking)
                val maxRank = preferences.getLong("maxRank", minMaxRanking.maxRanking)
                selectedMinMaxRanking = MinMaxRanking(
                    if(minRank > minMaxRanking.minRanking) minRank else minMaxRanking.minRanking,
                    0,
                    if (maxRank < minMaxRanking.maxRanking) maxRank else minMaxRanking.maxRanking
                )
                selectedTags = preferences.getStringSet("tags", setOf())?.toList()?: emptyList()
                frequency =  preferences.getString("frequency", "7")!!.toLong() * -1
                maxLifeTimeMessages = preferences.getString("max", "100")!!.toInt()
                top = preferences.getString("bulk", "-1")!!.toInt()
                abortOnError = preferences.getBoolean("abort", false)
                pauseOnError = preferences.getString("pause", "1000")!!.toLong()
                contacts = dbHelper.getFreshFilteredLimitedTopContacts(frequency,
                    if(lifeTimeLimit) maxLifeTimeMessages else -1,
                    if(top>0) top else count,
                    selectedTags,
                    selectedTags.size,
                    selectedMinMaxRanking.minRanking,
                    selectedMinMaxRanking.maxRanking,
                    !isLive)
                _status.postValue(INITIATED)
                val now = Calendar.getInstance().timeInMillis
                contacts.forEach {

                    val isContactHot = if (it.lastContact != null) {
                        ( - it.lastContact!!.time) < (abs(
                            frequency
                        ) * 86400000)
                    } else false

                    if (isContactHot) {
                        Log.e(
                            "MMMMMMMM",
                            "now $now last:${it.lastContact!!.time} < ${(abs(frequency) * 86400000)} freq:$frequency"
                        )
                        Log.e(
                            "WWWWWWWW", "Contact has received message ${
                                it.lastContact.formattedTimeElapsed(
                                    application,
                                    "never"
                                )
                            }. skipping..."
                        )
                    }
                }

                rankings = dbHelper.getFreshFilteredLimitedTopRankings(
                    selectedTags,
                    selectedTags.size,
                    !isLive).distinct().sorted()

                hasBroadcastErrors()
            }
        }
    }

    fun startBroadCast() {
        if(hasBroadcastErrors()) return
        job?.cancel() // Cancel the existing job if it's running
        var count = sent
        job = viewModelScope.launch {
            _status.postValue(STARTED)
            withContext(Dispatchers.IO) {
                _status.postValue(ONGOING)
                contacts.forEachIndexed { index, contact ->
                    if (!isBroadcasting() || !isInBroadcastWindow()) {
                        return@forEachIndexed
                    } else {
                        delay(ceil((carrierLimit/2).toDouble()).toLong())
                        var progress =
                            "${index + 1}/${contacts.size} sending to ${if (contact.name.isEmpty()) contact.phoneNumber else "${contact.name} - ${contact.phoneNumber}"}"
                        _progress.postValue(progress)
                        delay(ceil((carrierLimit/2).toDouble()).toLong())
                        val isContactHot = if (contact.lastContact != null) {
                            (Calendar.getInstance().timeInMillis - contact.lastContact!!.time) < (abs(frequency) * 86400000)
                            } else false

                        if(isContactHot) {
                            _progress.postValue("Contact has received message ${contact.lastContact.formattedTimeElapsed(
                                application,
                                "never"
                            )}. skipping...")
                        } else {
                            val success = GatewayServiceUtil.sendMessage(
                                application,
                                smsManager,
                                contact.phoneNumber,
                                broadcastMessage
                            )
                            if (success && !sendError) {
                                count++
                                val now = Calendar.getInstance().time
                                contact.lastContact = now
                                dbHelper.updateContact(contact)
                                dbHelper.insertMessage(
                                    Message(
                                        contactId = contact.id,
                                        message = broadcastMessage,
                                        timeStamp = now
                                    )
                                )
                            } else if (abortOnError) {
                                abort = true
                                _status.postValue(if (sendError) FAILED else ABORTED)
                            } else {
                                _progress.postValue("Error sending sms. Pausing broadcast for $pauseOnError")
                                delay(pauseOnError)
                                sendError = false
                            }

                            progress =
                                "${index + 1}/${contacts.size} ${if (success) "sms sent" else "error sending"} to ${if (contact.name.isEmpty()) contact.phoneNumber else "${contact.name} - ${contact.phoneNumber}"}"
                            _progress.postValue(progress)
                        }
                    }
                }
                sent = count
                if(status.value == ONGOING) {
                    _status.postValue(COMPLETED)
                }
                completed = true
            }
        }
    }

    private fun isInBroadcastWindow(): Boolean {
        val startTimeInMinutes = preferences.getInt("start_hour", 7) * 60 + preferences.getInt("start_minute", 0)
        var endTimeInMinutes = preferences.getInt("end_hour", 20) * 60 + preferences.getInt("end_minute", 30)
        var nowTimeInMinutes = Calendar.getInstance().let { calendar ->
            calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        }
        if(startTimeInMinutes > endTimeInMinutes) {
            endTimeInMinutes += 24 * 60
            nowTimeInMinutes += 24 * 60
        }
        return nowTimeInMinutes in startTimeInMinutes..endTimeInMinutes
    }

    private fun isBroadcasting(): Boolean {
        return !abort//&& status.value == ONGOING
    }

    private fun hasBroadcastErrors(): Boolean {
        var error = ""
        if(!isInBroadcastWindow())
            error= application.getString(R.string.broadcast_not_allowed_at_this_hour_go_to_settings)
        else if (contacts.isEmpty())
            error= application.getString(R.string.no_available_contacts_go_to_settings)
        else if(broadcastMessage.isEmpty() && isMessageModified)
            error= application.getString(R.string.message_cannot_be_empty)
        else if(contacts.mapNotNull { it.lastContact }
                .any { (Calendar.getInstance().timeInMillis - it.time) > (abs(frequency) * 86400000) })
            error = "Cannot send sms within ${abs(frequency)} days to the same contact"

        _error.postValue(error)

        return error.isNotEmpty()
    }

    fun abortBroadcast() {
        abort = true
        _status.postValue(ABORTED)
    }

    fun errorOnBroadcast() {
        sendError = true
    }

    fun addGenericBroadcastError() {
        genericErrorCount++
        if(genericErrorCount > genericErrorLimit) sendError = true
    }

    fun finishBroadcast() {
        _status.postValue(FINISHED)
        _status.postValue(STARTED)
    }

    fun findRankingForPercentile(percentile: Float): Long {
        val totalValues = rankings.size
        val position = (percentile / 100) * (totalValues - 1)
        if (position <= 0) return rankings.first()
        if (position >= totalValues - 1) return rankings.last()
        val lowerIndex = position.toInt()
        val upperIndex = lowerIndex + 1
        val lowerValue = rankings[lowerIndex]
        val upperValue = rankings[upperIndex]
        return (lowerValue + (position - lowerIndex) * (upperValue - lowerValue)).toLong()
    }

    fun findPercentileForRanking(value: Long): Double {
        val numValuesBelow = rankings.count { it < value }
        val totalValues = rankings.size
        return ceil((numValuesBelow.toDouble() / totalValues) * 100)
    }

    override fun onCleared() {
        CoroutineScope(Dispatchers.Main + Job()).launch {
            _status.value = CLEARED
            job?.cancel()
        }
        super.onCleared()
    }
}