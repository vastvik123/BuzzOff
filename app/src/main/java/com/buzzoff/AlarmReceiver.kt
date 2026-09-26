package com.buzzoff

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Fired by AlarmManager when an alarm (or snooze) is due. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0)
        if (intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false)) {
            AlarmStore.setSnooze(ctx, 0L, 0)
        } else {
            val alarm = AlarmStore.get(ctx, alarmId)
            if (alarm == null || !alarm.enabled || alarm.isSilenced(System.currentTimeMillis())) {
                AlarmScheduler.rescheduleAll(ctx)
                return
            }
            if (!alarm.isRepeating) AlarmStore.upsert(ctx, alarm.copy(enabled = false))
        }
        ContextCompat.startForegroundService(
            ctx, AlarmService.intent(ctx, AlarmService.ACTION_RING, alarmId)
        )
        AlarmScheduler.rescheduleAll(ctx)
    }
}

/** Alarms registered with AlarmManager are lost on reboot, so register them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> AlarmScheduler.rescheduleAll(ctx)
        }
    }
}
