package io.github.abdulroufsidhu.easy_twilio_caller

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager


private const val TAG = "easy-twilio-call-action"

class EasyTwilioCallNotificationActionHandler : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val window = window
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        handleIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        finish()
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        if (action != null) {
            when (action) {
                Constants.ACTION_INCOMING_CALL, Constants.ACTION_ACCEPT -> {
                    launchService(intent)
                    launchMainActivity(intent)
                }
                else -> launchService(intent)
            }
        }
    }

    private fun launchMainActivity(intent: Intent) {
        try {
            val launchIntent = Intent(intent)
//            launchIntent.setClass(this, VoiceActivity::class.java)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun launchService(intent: Intent) {
        val launchIntent = Intent(intent)
        launchIntent.setClass(this, EasyTwilioIncomingCallService::class.java)
        startService(launchIntent)
    }
}