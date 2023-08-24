package io.github.abdulroufsidhu.easy_twilio_caller

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioDeviceChangeListener
import com.twilio.audioswitch.AudioSwitch
import com.twilio.voice.Call
import com.twilio.voice.CallException
import com.twilio.voice.CallInvite
import com.twilio.voice.ConnectOptions
import com.twilio.voice.RegistrationException
import com.twilio.voice.RegistrationListener
import com.twilio.voice.Voice

private const val TAG = "easy-twilio-caller"

class EasyTwilioCaller(private val context: Context) {

    private val easyRecorder = EasyTwilioCallRecorder()
    private val audioSwitch = AudioSwitch(context)
    private val easySoundPoolManager = EasySoundPoolManager.getInstance(context)

    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun initialize(accessToken: String, audioDevices: AudioDeviceChangeListener) =
        initializeUnsafe(accessToken, audioDevices)

    @Deprecated(
        """above android api level 31 user requires 
        |android.permission.BLUTOOTH_CONNECT 
        |to actually perform calling via bluetooth devices like airpod 
        |so make sure to use the other initialized that forces the
        |permission check before use""", ReplaceWith("initialize")
    )
    fun initializeUnsafe(accessToken: String, audioDevices: AudioDeviceChangeListener) {
        audioSwitch.start(audioDevices)
//        registerForCallInvites(accessToken) {
//            throw it
//        }
    }

    companion object {
        fun getCallInviteFromIntent(intent: Intent): CallInvite? =
            intent.getParcelableExtra<CallInvite>(Constants.INCOMING_CALL_INVITE)

        fun getNotificationIdFromIntent(intent: Intent): Int =
            intent.getIntExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, 0)
    }

    /*
     * Register your FCM token with Twilio to receive incoming call invites
     */
    private fun registerForCallInvites(
        accessToken: String,
        onError: (RegistrationException) -> Unit
    ) {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task: Task<String?> ->
                if (!task.isSuccessful()) {
                    return@addOnCompleteListener
                }
                task.result?.let { fcmToken ->
                    Log.i(TAG, "Registering with FCM")
                    Voice.register(
                        accessToken,
                        Voice.RegistrationChannel.FCM,
                        fcmToken,
                        object : RegistrationListener {
                            override fun onRegistered(accessToken: String, fcmToken: String) {
                                Log.d(TAG, "onRegistered: fcmRegistered Successfully")
                            }

                            override fun onError(
                                registrationException: RegistrationException,
                                accessToken: String,
                                fcmToken: String
                            ) {
                                Log.w(TAG, "onError: $registrationException", registrationException)
                                onError(registrationException)
                            }
                        }
                    )
                }
            }
    }

    private fun getCallListener(
        onConnectFailure: ((Call, CallException) -> Unit)?,
        onRinging: ((Call) -> Unit)?,
        onConnected: ((Call) -> Unit)?,
        onReconnecting: ((Call, CallException) -> Unit)?,
        onReconnected: ((Call) -> Unit)?,
        onDisconnected: ((Call) -> Unit)?,
    ): Call.Listener {
        return object : Call.Listener {
            override fun onConnectFailure(call: Call, callException: CallException) {
                Log.w(TAG, "onConnectFailure: $callException", callException)
                audioSwitch.deactivate()
                easySoundPoolManager.stopRinging()
                onConnectFailure?.invoke(call, callException)
            }

            override fun onRinging(call: Call) {
                Log.i(TAG, "onRinging: ${call.from} -> ${call.to}")
                easySoundPoolManager.playRinging()
                onRinging?.invoke(call)
            }

            override fun onConnected(call: Call) {
                Log.i(TAG, "onConnected: ${call.from} -> ${call.to}")
                audioSwitch.activate()
                easySoundPoolManager.stopRinging()
                onConnected?.invoke(call)
            }

            override fun onReconnecting(call: Call, callException: CallException) {
                Log.w(TAG, "onReconnecting: $callException", callException)
                onReconnecting?.invoke(call, callException)
            }

            override fun onReconnected(call: Call) {
                Log.i(TAG, "onReconnected: ${call.from} -> ${call.to}")
                onReconnected?.invoke(call)
            }

            override fun onDisconnected(call: Call, callException: CallException?) {
                Log.i(TAG, "onDisconnected: ${call.from} -> ${call.to}")
                audioSwitch.deactivate()
                audioSwitch.stop()
                easySoundPoolManager.playDisconnect()
                easySoundPoolManager.stopRinging()
                easySoundPoolManager.release()
                onDisconnected?.invoke(call)
            }
        }
    }

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun accept(
        callInvite: CallInvite,
        onConnected: ((Call) -> Unit)? = null,
        onConnectFailure: ((Call, CallException) -> Unit)? = null,
        onRinging: ((Call) -> Unit)? = null,
        onReconnecting: ((Call, CallException) -> Unit)? = null,
        onReconnected: ((Call) -> Unit)? = null,
        onDisconnected: ((Call) -> Unit)? = null,
    ) = callInvite.accept(
        context,
        getCallListener(
            onRinging = onRinging,
            onConnected = onConnected,
            onConnectFailure = onConnectFailure,
            onReconnecting = onReconnecting,
            onReconnected = onReconnected,
            onDisconnected = onDisconnected
        )
    )

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun connect(
        accessToken: String,
        to: String,
        from: String,
        onConnected: ((Call) -> Unit)? = null,
        onConnectFailure: ((Call, CallException) -> Unit)? = null,
        onRinging: ((Call) -> Unit)? = null,
        onReconnecting: ((Call, CallException) -> Unit)? = null,
        onReconnected: ((Call) -> Unit)? = null,
        onDisconnected: ((Call) -> Unit)? = null,
    ) {
        val params = HashMap<String, String>()
        // Place a call
        params.put("To", to)
        params.put("From", from)

        val connectOptions: ConnectOptions = ConnectOptions.Builder(accessToken)
            .params(params)
            .build()
        Log.d(TAG, "connect: calling to ${params}")
        Voice.connect(
            context,
            connectOptions,
            getCallListener(
                onRinging = onRinging,
                onConnected = onConnected,
                onConnectFailure = onConnectFailure,
                onReconnecting = onReconnecting,
                onReconnected = onReconnected,
                onDisconnected = onDisconnected
            ),
        )
    }

    fun disconnect(call: Call) = call.disconnect().also { easySoundPoolManager.stopRinging() }

    fun toggleHold(call: Call) = call.hold(call.isOnHold.not())

    fun toggleMute(call: Call) = call.mute(call.isMuted.not())

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun startRecording(path: String) = easyRecorder.startRecording(path)

    fun stopRecording() = easyRecorder.stopRecording()

    fun availableAudioDevices() = audioSwitch.availableAudioDevices
    fun setAudioDevice(audioDevice: AudioDevice) = audioSwitch.selectDevice(audioDevice)
    fun getActiveAudioDevice() = audioSwitch.selectedAudioDevice
    fun getActiveAudioDeviceIndex() =
        audioSwitch.availableAudioDevices.indexOf(audioSwitch.selectedAudioDevice)

}