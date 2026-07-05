package com.example.voicetodo

import android.app.Application
import com.example.voicetodo.notify.NotificationHelper

class VoiceTodoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}
