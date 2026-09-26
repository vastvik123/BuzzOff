package com.buzzoff

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalTime

/** Foreground service that plays the alarm sound and shows the full-screen alarm. */
class AlarmService : Service() {
    companion object {
        const val ACTION_RING = "com.buzzoff.RING"
        const val ACTION_SNOOZE = "com.buzzoff.SNOOZE"
        const val ACTION_DISMISS = "com.buzzoff.DISMISS"
        /** Alarm id used by the "Test alarm" button in Settings. */
        const val TEST_ALARM_ID = 0
        private const val CHANNEL_ID = "ringing"
        private const val NOTIFICATION_ID = 1
        private const val RAMP_STEPS = 30 // One step per second.

        /** Id of the alarm ringing right now, or null when silent. */
        val ringingAlarmId = MutableStateFlow<Int?>(null)

        fun intent(ctx: Context, action: String, alarmId: Int = 0): Intent =
            Intent(ctx, AlarmService::class.java)
                .setAction(action)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stopRinging() }
    private var rampStep = 0
    private val ramp = object : Runnable {
        override fun run() {
            rampStep++
            val volume = (0.15f + 0.85f * rampStep / RAMP_STEPS).coerceAtMost(1f)
            player?.setVolume(volume, volume)
            if (rampStep < RAMP_STEPS) handler.postDelayed(this, 1_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> ring(intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0))
            ACTION_SNOOZE -> {
                ringingAlarmId.value?.let { if (it != TEST_ALARM_ID) AlarmScheduler.snooze(this, it) }
                stopRinging()
            }
            else -> stopRinging()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun ring(alarmId: Int) {
        val alarm = AlarmStore.get(this, alarmId)
        ringingAlarmId.value = alarmId
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(alarm),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        if (player == null) startSound(alarm?.ringtone)
        if (vibrator == null && alarm?.vibrate != false) startVibration()
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, Prefs.autoStopMinutes(this) * 60_000L)
    }

    private fun stopRinging() {
        release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun release() {
        handler.removeCallbacksAndMessages(null)
        player?.run {
            runCatching { stop() }
            release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        ringingAlarmId.value = null
    }

    private fun startSound(ringtone: String?) {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val candidates = listOfNotNull(ringtone?.let(Uri::parse)) + listOf(
            RingtoneManager.TYPE_ALARM,
            RingtoneManager.TYPE_RINGTONE,
            RingtoneManager.TYPE_NOTIFICATION,
        ).mapNotNull { RingtoneManager.getDefaultUri(it) }
        val ramping = Prefs.rampVolume(this)

        for (uri in candidates) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(attributes)
                mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.prepare()
                if (ramping) mp.setVolume(0.15f, 0.15f)
                mp.start()
                player = mp
                if (ramping) {
                    rampStep = 0
                    handler.postDelayed(ramp, 1_000)
                }
                return
            } catch (e: Exception) {
                Log.w("BuzzOff", "Could not play $uri", e)
                mp.release()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0), attributes)
        vibrator = v
    }

    private fun buildNotification(alarm: Alarm?): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID, "Ringing alarm", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null) // The service plays the sound itself.
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val fullScreen = PendingIntent.getActivity(
            this, 0,
            Intent(this, RingingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun serviceAction(action: String, requestCode: Int) = PendingIntent.getService(
            this, requestCode, intent(this, action), PendingIntent.FLAG_IMMUTABLE
        )
        val now = LocalTime.now()
        val title = alarm?.label?.takeIf { it.isNotBlank() }
            ?: alarm?.let { AlarmStore.category(this, it.categoryId)?.name }
            ?: "BuzzOff"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setColor(getColor(R.color.brand_violet))
            .setContentTitle(title)
            .setContentText("Alarm · ${formatTime(this, now.hour, now.minute)}")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, "Snooze", serviceAction(ACTION_SNOOZE, 1))
            .addAction(0, "Dismiss", serviceAction(ACTION_DISMISS, 2))
            .build()
    }
}
