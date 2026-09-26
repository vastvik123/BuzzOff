package com.buzzoff

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/** A group of alarms, e.g. "Morning". "I'm up" silences one category at a time. */
data class Category(val id: Int, val name: String)

data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    /** DayOfWeek values (1 = Monday … 7 = Sunday). Empty means ring once. */
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    /** Set by "I'm up": the alarm stays silent until this time (epoch millis). */
    val skipUntil: Long = 0L,
    val categoryId: Int = 1,
    val label: String = "",
    /** Ringtone URI, or null for the phone's default alarm sound. */
    val ringtone: String? = null,
    val vibrate: Boolean = true,
) {
    val isRepeating get() = days.isNotEmpty()

    fun isSilenced(nowMillis: Long) = skipUntil > nowMillis

    /** The next time this alarm should ring after [now], honouring [skipUntil]. */
    fun nextRing(now: ZonedDateTime): ZonedDateTime {
        val skip = Instant.ofEpochMilli(skipUntil).atZone(now.zone).minusSeconds(1)
        val from = if (skip.isAfter(now)) skip else now
        var date = from.toLocalDate()
        repeat(8) {
            val candidate = ZonedDateTime.of(date, LocalTime.of(hour, minute), from.zone)
            if (candidate.isAfter(from) && (days.isEmpty() || candidate.dayOfWeek.value in days)) {
                return candidate
            }
            date = date.plusDays(1)
        }
        error("No ring time found for alarm $id")
    }
}

enum class Frequency(val label: String, val days: Set<Int>?) {
    ONCE("Once", emptySet()),
    DAILY("Every day", (1..7).toSet()),
    WEEKDAYS("Weekdays", (1..5).toSet()),
    WEEKENDS("Weekends", setOf(6, 7)),
    CUSTOM("Custom", null);

    companion object {
        fun of(days: Set<Int>): Frequency = entries.firstOrNull { it.days == days } ?: CUSTOM
    }
}

fun repeatLabel(days: Set<Int>): String {
    val frequency = Frequency.of(days)
    if (frequency != Frequency.CUSTOM) return frequency.label
    return days.sorted().joinToString(", ") {
        DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
}
