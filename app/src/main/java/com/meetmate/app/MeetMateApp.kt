package com.meetmate.app

import android.app.Application
import com.meetmate.app.notification.NotificationHelper

class MeetMateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }
}
