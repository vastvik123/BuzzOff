package com.buzzoff

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Alarms and categories persisted as JSON in SharedPreferences, exposed as flows for the UI. */
object AlarmStore {
    private const val KEY_ALARMS = "alarms"
    private const val KEY_CATEGORIES = "categories"
    private const val KEY_SNOOZE_AT = "snoozeAt"
    private const val KEY_SNOOZE_ALARM = "snoozeAlarmId"
    private val DEFAULT_CATEGORIES = listOf(Category(1, "Morning"))

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms
    private val _categories = MutableStateFlow(DEFAULT_CATEGORIES)
    val categories: StateFlow<List<Category>> = _categories

    /** Pending snooze as (ring time in epoch millis, original alarm id); time 0 means none. */
    private val _snooze = MutableStateFlow(0L to 0)
    val snooze: StateFlow<Pair<Long, Int>> = _snooze
    private var loaded = false

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("alarms", Context.MODE_PRIVATE)

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        val p = prefs(ctx)
        val categories = parseCategories(p.getString(KEY_CATEGORIES, null)).ifEmpty { DEFAULT_CATEGORIES }
        val ids = categories.map { it.id }.toSet()
        _categories.value = categories
        // Alarms whose category no longer exists move to the first category.
        _alarms.value = parseAlarms(p.getString(KEY_ALARMS, "[]")!!).map {
            if (it.categoryId in ids) it else it.copy(categoryId = categories.first().id)
        }
        _snooze.value = p.getLong(KEY_SNOOZE_AT, 0L) to p.getInt(KEY_SNOOZE_ALARM, 0)
        loaded = true
    }

    fun all(ctx: Context): List<Alarm> {
        load(ctx)
        return _alarms.value
    }

    fun get(ctx: Context, id: Int): Alarm? = all(ctx).find { it.id == id }

    fun newId(ctx: Context): Int = (all(ctx).maxOfOrNull { it.id } ?: 0) + 1

    @Synchronized
    fun save(ctx: Context, alarms: List<Alarm>) {
        load(ctx)
        val sorted = alarms.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
        _alarms.value = sorted
        prefs(ctx).edit { putString(KEY_ALARMS, alarmsToJson(sorted)) }
    }

    fun upsert(ctx: Context, alarm: Alarm) = save(ctx, all(ctx).filter { it.id != alarm.id } + alarm)

    fun delete(ctx: Context, id: Int) = save(ctx, all(ctx).filter { it.id != id })

    fun categories(ctx: Context): List<Category> {
        load(ctx)
        return _categories.value
    }

    fun category(ctx: Context, id: Int): Category? = categories(ctx).find { it.id == id }

    fun addCategory(ctx: Context, name: String): Category {
        val category = Category((categories(ctx).maxOfOrNull { it.id } ?: 0) + 1, name)
        saveCategories(ctx, categories(ctx) + category)
        return category
    }

    fun renameCategory(ctx: Context, id: Int, name: String) =
        saveCategories(ctx, categories(ctx).map { if (it.id == id) it.copy(name = name) else it })

    /** Deletes a category together with its alarms. The last category can't be deleted. */
    fun deleteCategory(ctx: Context, id: Int) {
        if (categories(ctx).size <= 1) return
        saveCategories(ctx, categories(ctx).filter { it.id != id })
        save(ctx, all(ctx).filter { it.categoryId != id })
    }

    @Synchronized
    private fun saveCategories(ctx: Context, categories: List<Category>) {
        _categories.value = categories
        prefs(ctx).edit { putString(KEY_CATEGORIES, categoriesToJson(categories)) }
    }

    fun snooze(ctx: Context): Pair<Long, Int> {
        load(ctx)
        return _snooze.value
    }

    fun setSnooze(ctx: Context, at: Long, alarmId: Int) {
        _snooze.value = at to alarmId
        prefs(ctx).edit {
            putLong(KEY_SNOOZE_AT, at)
            putInt(KEY_SNOOZE_ALARM, alarmId)
        }
    }

    private fun alarmsToJson(alarms: List<Alarm>): String = JSONArray().apply {
        for (a in alarms) {
            put(
                JSONObject()
                    .put("id", a.id)
                    .put("hour", a.hour)
                    .put("minute", a.minute)
                    .put("days", JSONArray(a.days.sorted()))
                    .put("enabled", a.enabled)
                    .put("skipUntil", a.skipUntil)
                    .put("category", a.categoryId)
                    .put("label", a.label)
                    .put("ringtone", a.ringtone ?: JSONObject.NULL)
                    .put("vibrate", a.vibrate)
            )
        }
    }.toString()

    private fun parseAlarms(json: String): List<Alarm> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val days = o.getJSONArray("days")
            Alarm(
                id = o.getInt("id"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                days = (0 until days.length()).map { days.getInt(it) }.toSet(),
                enabled = o.getBoolean("enabled"),
                skipUntil = o.optLong("skipUntil", 0L),
                categoryId = o.optInt("category", 1),
                label = o.optString("label", ""),
                ringtone = if (o.isNull("ringtone")) null else o.optString("ringtone"),
                vibrate = o.optBoolean("vibrate", true),
            )
        }
    }

    private fun categoriesToJson(categories: List<Category>): String = JSONArray().apply {
        for (c in categories) put(JSONObject().put("id", c.id).put("name", c.name))
    }.toString()

    private fun parseCategories(json: String?): List<Category> {
        if (json == null) return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Category(o.getInt("id"), o.getString("name"))
        }
    }
}

/** User preferences from the Settings screen. */
object Prefs {
    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun snoozeMinutes(ctx: Context) = prefs(ctx).getInt("snoozeMinutes", 5)
    fun setSnoozeMinutes(ctx: Context, value: Int) = prefs(ctx).edit { putInt("snoozeMinutes", value) }

    fun autoStopMinutes(ctx: Context) = prefs(ctx).getInt("autoStopMinutes", 10)
    fun setAutoStopMinutes(ctx: Context, value: Int) = prefs(ctx).edit { putInt("autoStopMinutes", value) }

    fun rampVolume(ctx: Context) = prefs(ctx).getBoolean("rampVolume", true)
    fun setRampVolume(ctx: Context, value: Boolean) = prefs(ctx).edit { putBoolean("rampVolume", value) }
}
