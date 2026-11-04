package com.example.studplan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("studplan", Context.MODE_PRIVATE)

    // ---------- Задания ----------
    fun loadTasks(): List<Task> {
        val text = prefs.getString("tasks", null) ?: return emptyList()
        val arr = JSONArray(text)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Task(
                id = o.getInt("id"),
                title = o.getString("title"),
                subject = o.getString("subject"),
                done = o.getBoolean("done"),
                priority = o.optInt("priority", 1),
                deadline = o.optLong("deadline", 0L),
                doneAt = o.optLong("doneAt", 0L)
            )
        }
    }

    fun saveTasks(tasks: List<Task>) {
        val arr = JSONArray()
        tasks.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("subject", t.subject)
                    .put("done", t.done)
                    .put("priority", t.priority)
                    .put("deadline", t.deadline)
                    .put("doneAt", t.doneAt)
            )
        }
        prefs.edit().putString("tasks", arr.toString()).apply()
    }

    // ---------- Расписание ----------
    fun loadLessons(): List<Lesson> {
        // Просто возвращаем пустой список, если данных нет
        val text = prefs.getString("lessons", null) ?: return emptyList()
        val arr = JSONArray(text)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Lesson(
                o.getInt("id"),
                o.getInt("day"),
                o.getString("time"),
                o.getString("subject"),
                o.getString("room"),
                o.optLong("date", 0L),
                o.optString("end", ""),
                o.optString("teacher", ""),
                o.optString("lessonType", ""),
                o.optString("source", "manual")
            )
        }
    }

    fun saveLessons(lessons: List<Lesson>) {
        val arr = JSONArray()
        lessons.forEach { l ->
            arr.put(
                JSONObject()
                    .put("id", l.id)
                    .put("day", l.day)
                    .put("time", l.time)
                    .put("subject", l.subject)
                    .put("room", l.room)
                    .put("date", l.date)
                    .put("end", l.end)
                    .put("teacher", l.teacher)
                    .put("lessonType", l.lessonType)
                    .put("source", l.source)
            )
        }
        prefs.edit().putString("lessons", arr.toString()).apply()
    }

    fun saveAsuSyncTime(time: Long) {
        prefs.edit().putLong("asu_sync_time", time).apply()
    }

    fun loadAsuSyncTime(): Long = prefs.getLong("asu_sync_time", 0L)

    // ---------- Тема ----------
    fun loadDark(default: Boolean): Boolean = prefs.getBoolean("dark", default)

    fun saveDark(value: Boolean) {
        prefs.edit().putBoolean("dark", value).apply()
    }

    // ---------- Уведомления ----------
    fun loadNotif(): NotifSettings = NotifSettings(
        enabled = prefs.getBoolean("n_enabled", false),
        dayBefore = prefs.getBoolean("n_day_before", true),
        dayBeforeTime = prefs.getString("n_day_before_time", "18:00") ?: "18:00",
        sameDay = prefs.getBoolean("n_same_day", true),
        sameDayTime = prefs.getString("n_same_day_time", "09:00") ?: "09:00",
        lessons = prefs.getBoolean("n_lessons", true),
        lessonBefore = prefs.getInt("n_lesson_before", 15)
    )

    fun saveNotif(s: NotifSettings) {
        prefs.edit()
            .putBoolean("n_enabled", s.enabled)
            .putBoolean("n_day_before", s.dayBefore)
            .putString("n_day_before_time", s.dayBeforeTime)
            .putBoolean("n_same_day", s.sameDay)
            .putString("n_same_day_time", s.sameDayTime)
            .putBoolean("n_lessons", s.lessons)
            .putInt("n_lesson_before", s.lessonBefore)
            .apply()
    }
}