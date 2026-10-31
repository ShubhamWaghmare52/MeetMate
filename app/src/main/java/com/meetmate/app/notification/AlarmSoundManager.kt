package com.meetmate.app.notification

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log

object AlarmSoundManager {

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stop() }

    fun play(context: Context) {
        stop()

        try {
            val soundUri = NotificationHelper.getAlarmSoundUri(context)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, soundUri)
                isLooping = true
                prepare()
                start()
            }
            handler.postDelayed(stopRunnable, 60_000L)
            Log.d("MeetMateAlarm", "Alarm sound started, will auto-stop in 60s")
        } catch (e: Exception) {
            Log.e("MeetMateAlarm", "Failed to play alarm sound", e)
        }
    }

    fun stop() {
        handler.removeCallbacks(stopRunnable)
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
                Log.d("MeetMateAlarm", "Alarm sound stopped")
            } catch (_: Exception) {}
        }
        mediaPlayer = null
    }
}
