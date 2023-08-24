package io.github.abdulroufsidhu.easy_twilio_caller

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.twilio.voice.CallException
import com.twilio.voice.CallInvite
import com.twilio.voice.CancelledCallInvite
import com.twilio.voice.MessageListener
import com.twilio.voice.Voice

private const val TAG = "easy-twilio-fcm"

class EasyTwilioFCM : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Received onMessageReceived()")
        Log.d(TAG, "Bundle data: " + message.getData())
        Log.d(TAG, "From: " + message.getFrom())

        // Check if message contains a data payload.

        // Check if message contains a data payload.
        if (message.getData().size > 0) {
            val valid: Boolean =
                Voice.handleMessage(this, message.getData(), object : MessageListener {
                    override fun onCallInvite(callInvite: CallInvite) {
                        val notificationId = System.currentTimeMillis().toInt()
                        handleInvite(callInvite, notificationId)
                    }

                    override fun onCancelledCallInvite(
                        cancelledCallInvite: CancelledCallInvite,
                        callException: CallException?
                    ) {
                        handleCanceledCallInvite(cancelledCallInvite)
                    }
                })
            if (!valid) {
                Log.e(
                    TAG, "The message was not a valid Twilio Voice SDK payload: " +
                            message.getData()
                )
            }
        }
    }


    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val intent = Intent(Constants.ACTION_FCM_TOKEN)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun handleInvite(callInvite: CallInvite, notificationId: Int) {
        val intent = Intent(
            this,
            EasyTwilioIncomingCallService::class.java
        )
        intent.setAction(Constants.ACTION_INCOMING_CALL)
        intent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId)
        intent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite)
        startService(intent)
    }

    private fun handleCanceledCallInvite(cancelledCallInvite: CancelledCallInvite) {
        val intent = Intent(
            this,
            EasyTwilioIncomingCallService::class.java
        )
        intent.setAction(Constants.ACTION_CANCEL_CALL)
        intent.putExtra(Constants.CANCELLED_CALL_INVITE, cancelledCallInvite)
        startService(intent)
    }

}