package com.qedron.gateway

import android.app.Application
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
import kotlin.math.ceil

class BroadcastViewModel(private val application: Application) : AndroidViewModel(application) {
    companion object{
        const val STARTED = "started"
        const val INITIATED = "initiated"
        const val ONGOING = "ongoing"
        const val ABORTED = "aborted"
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
    private var frequency = -7
    private var maxLifeTimeMessages = 100
    private var carrierLimit = 1000L
    private var top = -1

    var isMessageModified = false
        set(value) {
            field = value
//            hasBroadcastErrors()
        }
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
    var minMaxRanking: MinMaxRanking = MinMaxRanking(0, Int.MAX_VALUE)
        set(value) {
            field = value
            hasBroadcastErrors()
        }
    var selectedMinMaxRanking: MinMaxRanking = MinMaxRanking(0, 0)
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

    lateinit var contacts: List<Contact>

    private val _progress = MutableLiveData<String>()
    val progress: LiveData<String> = _progress

    private val _status = MutableLiveData<String>(STARTED)
    val status: LiveData<String> = _status

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    fun initBroadCast() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                abort = false
                sent = 0
                completed = false
                tags = dbHelper.getAllUniqueTags()
                minMaxRanking = dbHelper.getMinAndMaxRanking()
                isLive = preferences.getBoolean("live", false)
                count = dbHelper.countContacts(!isLive)
                carrierLimit = preferences.getString("delay", "1000")!!.toLong()
                lifeTimeLimit = preferences.getBoolean("limit", false)
                val minRank = preferences.getInt("minRank", minMaxRanking.minRanking)
                val maxRank = preferences.getInt("maxRank", minMaxRanking.minRanking)
                selectedMinMaxRanking = MinMaxRanking(if(minRank > minMaxRanking.minRanking) minRank else minMaxRanking.minRanking, if (maxRank < minMaxRanking.maxRanking) maxRank else minMaxRanking.maxRanking)
                selectedTags = preferences.getStringSet("tags", setOf())?.toList()?: emptyList()
                frequency =  preferences.getString("frequency", "7")!!.toInt() * -1
                maxLifeTimeMessages = preferences.getString("max", "100")!!.toInt()
                top = preferences.getString("bulk", "-1")!!.toInt()
                contacts = if(!isLive) dbHelper.getFreshTopTestContacts(if(top>0) top else count)
                    else if(lifeTimeLimit) dbHelper.getFreshLimitedTopContacts(frequency, maxLifeTimeMessages, if(top>0) top else count)
                else dbHelper.getFreshTopContacts(frequency, if(top>0) top else count)
                _status.postValue(INITIATED)
//                _error.postValue("")
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
                            "${index + 1}/${contacts.size} sending sms to ${if (contact.name.isEmpty()) contact.phoneNumber else "${contact.name} - ${contact.phoneNumber}"}"
                        _progress.postValue(progress)
                        delay(ceil((carrierLimit/2).toDouble()).toLong())
                        val success = GatewayServiceUtil.sendMessage(
                            application,
                            smsManager,
                            contact.phoneNumber,
                            broadcastMessage
                        )
                        if (success) {
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
                        }
                        progress =
                            "${index + 1}/${contacts.size} ${if(success) "sent sms" else "error sending"} to ${if (contact.name.isEmpty()) contact.phoneNumber else "${contact.name} - ${contact.phoneNumber}"}"
                        _progress.postValue(progress)
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
        val endTimeInMinutes = preferences.getInt("end_hour", 20) * 60 + preferences.getInt("end_minute", 30)
        val nowTimeInMinutes = Calendar.getInstance().let { calendar ->
            calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        }
        return nowTimeInMinutes in startTimeInMinutes..endTimeInMinutes
    }

    private fun isBroadcasting(): Boolean {
        return !abort //&& status.value == ONGOING
    }

    private fun hasBroadcastErrors(): Boolean {
        var error = ""
        if(!isInBroadcastWindow())
            error="Broadcast not allowed at this time of day."
        else if (this::contacts.isInitialized && contacts.isEmpty())
            error="No available contacts."
        else if(broadcastMessage.isEmpty() && isMessageModified)
            error="Message cannot be empty"
        _error.postValue(error)
        return error.isNotEmpty()
    }

    fun killBroadcast() {
        job?.cancel()
        _status.postValue(KILLED)
    }

    fun abortBroadcast() {
        abort = true
        _status.postValue(ABORTED)
    }

    fun finishBroadcast() {
        _status.postValue(FINISHED)
        _status.postValue(STARTED)
    }

    override fun onCleared() {
        CoroutineScope(Dispatchers.Main + Job()).launch {
            _status.value = CLEARED
            job?.cancel()
        }
        super.onCleared()
    }
}