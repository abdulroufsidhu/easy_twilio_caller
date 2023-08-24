package io.github.abdulroufsidhu.easy_twilio_caller

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.twilio.voice.CallInvite


private const val TAG = "easy-twilio-call-srvc"

class EasyTwilioIncomingCallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    var easyTwilioCaller: EasyTwilioCaller? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val action = intent.action
        if (action != null) {
            val callInvite = EasyTwilioCaller.getCallInviteFromIntent(intent)
            val notificationId = EasyTwilioCaller.getNotificationIdFromIntent(intent)
            callInvite?.let { callInvite ->
                when (action) {
                    Constants.ACTION_INCOMING_CALL -> handleIncomingCall(callInvite, notificationId)
                    Constants.ACTION_ACCEPT -> accept(callInvite, notificationId)
                    Constants.ACTION_REJECT -> reject(callInvite)
                    Constants.ACTION_CANCEL_CALL -> handleCancelledCall(intent)
                    else -> {}
                }
            }
        }
        return START_NOT_STICKY
    }


    private fun createNotification(
        callInvite: CallInvite,
        notificationId: Int,
        channelImportance: Int
    ): Notification? {
        val intent = Intent(this, EasyTwilioCallNotificationActionHandler::class.java)
        intent.setAction(Constants.ACTION_INCOMING_CALL_NOTIFICATION)
        intent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId)
        intent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent =
            PendingIntent.getActivity(this, notificationId, intent, PendingIntent.FLAG_IMMUTABLE)
        /*
         * Pass the notification id and call sid to use as an identifier to cancel the
         * notification later
         */
        val extras = Bundle()
        extras.putString(Constants.CALL_SID_KEY, callInvite.callSid)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            buildNotification(
                callInvite.from + " is calling.",
                pendingIntent,
                extras,
                callInvite,
                notificationId,
                createChannel(channelImportance)
            )
        } else {
            NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_call_end)
                .setContentTitle(Constants.APP_NAME)
                .setContentText(callInvite.from + " is calling...")
                .setAutoCancel(true)
                .setExtras(extras)
                .setContentIntent(pendingIntent)
                .setGroup("test_app_notification")
                .setCategory(Notification.CATEGORY_CALL)
                .setColor(Color.rgb(214, 10, 37)).build()
        }
    }

    /**
     * Build a notification.
     *
     * @param text          the text of the notification
     * @param pendingIntent the body, pending intent for the notification
     * @param extras        extras passed with the notification
     * @return the builder
     */
    @TargetApi(Build.VERSION_CODES.O)
    private fun buildNotification(
        text: String, pendingIntent: PendingIntent, extras: Bundle,
        callInvite: CallInvite,
        notificationId: Int,
        channelId: String
    ): Notification? {
        val rejectIntent = Intent(
            applicationContext,
            EasyTwilioIncomingCallService::class.java
        )
        rejectIntent.setAction(Constants.ACTION_REJECT)
        rejectIntent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite)
        rejectIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId)
        val piRejectIntent = PendingIntent.getService(
            applicationContext,
            notificationId,
            rejectIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val acceptIntent = Intent(
            applicationContext,
            EasyTwilioCallNotificationActionHandler::class.java
        )
        acceptIntent.setAction(Constants.ACTION_ACCEPT)
        acceptIntent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite)
        acceptIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId)
        acceptIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val piAcceptIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            acceptIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder: Notification.Builder = Notification.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_call_end)
            .setContentTitle(Constants.APP_NAME)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_CALL)
            .setExtras(extras)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_call_end, Constants.CALL_DECLINE_STR, piRejectIntent)
            .addAction(R.drawable.ic_call, Constants.CALL_ACCEPT_STR, piAcceptIntent)
            .setFullScreenIntent(pendingIntent, true)
        return builder.build()
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createChannel(channelImportance: Int): String {
        var callInviteChannel = NotificationChannel(
            Constants.VOICE_CHANNEL_HIGH_IMPORTANCE,
            "Primary Voice Channel", NotificationManager.IMPORTANCE_HIGH
        )
        var channelId = Constants.VOICE_CHANNEL_HIGH_IMPORTANCE
        if (channelImportance == NotificationManager.IMPORTANCE_LOW) {
            callInviteChannel = NotificationChannel(
                Constants.VOICE_CHANNEL_LOW_IMPORTANCE,
                "Primary Voice Channel", NotificationManager.IMPORTANCE_LOW
            )
            channelId = Constants.VOICE_CHANNEL_LOW_IMPORTANCE
        }
        callInviteChannel.lightColor = Color.GREEN
        callInviteChannel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(callInviteChannel)
        return channelId
    }

    private fun accept(callInvite: CallInvite, notificationId: Int,) {
        easyTwilioCaller = EasyTwilioCaller(this@EasyTwilioIncomingCallService)
        easyTwilioCaller?.accept(callInvite)
    }

    private fun reject(callInvite: CallInvite) {
        endForeground()
        callInvite.reject(applicationContext)
    }

    private fun handleCancelledCall(intent: Intent) {
        endForeground()
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun handleIncomingCall(callInvite: CallInvite, notificationId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setCallInProgressNotification(callInvite, notificationId)
        }
    }

    private fun endForeground() {
        stopSelf()
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun setCallInProgressNotification(callInvite: CallInvite, notificationId: Int) {
        if (isAppVisible()) {
            Log.i(TAG, "setCallInProgressNotification - app is visible.")
            startForeground(
                notificationId,
                createNotification(callInvite, notificationId, NotificationManager.IMPORTANCE_LOW)
            )
        } else {
            Log.i(TAG, "setCallInProgressNotification - app is NOT visible.")
            startForeground(
                notificationId,
                createNotification(callInvite, notificationId, NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }


    private fun isAppVisible(): Boolean {
        return ProcessLifecycleOwner
            .get()
            .lifecycle
            .currentState
            .isAtLeast(Lifecycle.State.STARTED)
    }

}