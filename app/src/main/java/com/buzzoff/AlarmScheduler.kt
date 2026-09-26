package com.buzzoff

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZonedDateTime

/** Keeps the system AlarmManager in sync with the alarms in [AlarmStore]. */
object AlarmScheduler {
    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_SNOOZE = "snooze"
    private const val SNOOZE_REQUEST_CODE = Int.MAX_VALUE

    private fun alarmManager(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager(ctx).canScheduleExactAlarms()

    fun rescheduleAll(ctx: Context) {
        val now = ZonedDateTime.now()
        for (alarm in AlarmStore.all(ctx)) {
            if (alarm.enabled) {
                set(ctx, alarm.id, alarm.id, false, alarm.nextRing(now).toInstant().toEpochMilli())
            } else {
                cancel(ctx, alarm.id)
            }
        }
        val (snoozeAt, snoozeAlarmId) = AlarmStore.snooze(ctx)
        if (snoozeAt > System.currentTimeMillis()) {
            set(ctx, SNOOZE_REQUEST_CODE, snoozeAlarmId, true, snoozeAt)
        } else {
            cancel(ctx, SNOOZE_REQUEST_CODE)
        }
    }

    fun snooze(ctx: Context, alarmId: Int) {
        val at = System.currentTimeMillis() + Prefs.snoozeMinutes(ctx) * 60_000L
        AlarmStore.setSnooze(ctx, at, alarmId)
        rescheduleAll(ctx)
    }

    fun cancelSnooze(ctx: Context) {
        AlarmStore.setSnooze(ctx, 0L, 0)
        rescheduleAll(ctx)
    }

    /**
     * "I'm up" for one category: its repeating alarms skip the rest of today,
     * its one-time alarms still due today are switched off, and its snooze is dropped.
     */
    fun silenceCategory(ctx: Context, categoryId: Int) {
        val now = ZonedDateTime.now()
        val endOfDay = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        val endMillis = endOfDay.toInstant().toEpochMilli()
        AlarmStore.save(ctx, AlarmStore.all(ctx).map { alarm ->
            when {
                alarm.categoryId != categoryId || !alarm.enabled -> alarm
                alarm.isRepeating -> alarm.copy(skipUntil = endMillis)
                // skipUntil marks it so "Turn back on" can restore it.
                alarm.nextRing(now).isBefore(endOfDay) -> alarm.copy(enabled = false, skipUntil = endMillis)
                else -> alarm
            }
        })
        val (_, snoozedId) = AlarmStore.snooze(ctx)
        if (AlarmStore.get(ctx, snoozedId)?.categoryId == categoryId) AlarmStore.setSnooze(ctx, 0L, 0)
        rescheduleAll(ctx)
    }

    /** Undoes [silenceCategory]. */
    fun unsilenceCategory(ctx: Context, categoryId: Int) {
        val now = System.currentTimeMillis()
        AlarmStore.save(ctx, AlarmStore.all(ctx).map {
            if (it.categoryId == categoryId && it.isSilenced(now)) it.copy(enabled = true, skipUntil = 0L) else it
        })
        rescheduleAll(ctx)
    }

    fun cancel(ctx: Context, requestCode: Int) {
        alarmManager(ctx).cancel(operation(ctx, requestCode, 0, false))
    }

    private fun set(ctx: Context, requestCode: Int, alarmId: Int, snooze: Boolean, triggerAt: Long) {
        if (!canScheduleExact(ctx)) return // The home screen tells the user to grant this.
        val showIntent = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager(ctx).setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showIntent),
            operation(ctx, requestCode, alarmId, snooze),
        )
    }

    private fun operation(ctx: Context, requestCode: Int, alarmId: Int, snooze: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            requestCode,
            Intent(ctx, AlarmReceiver::class.java)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_SNOOZE, snooze),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
