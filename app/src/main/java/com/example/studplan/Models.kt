package com.example.studplan

import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class UserMode(val title: String, val lessonMinutes: Int) {
    Student("Студент", 90)
}

data class Task(
    val id: Int,
    val title: String,
    val subject: String,
    val done: Boolean = false,
    val priority: Int = 1,      // 0 = низкий, 1 = средний, 2 = высокий
    val deadline: Long = 0L,    // 0 = срока нет
    val doneAt: Long = 0L       // когда отмечено выполненным, 0 = не выполнено
)

data class Lesson(
    val id: Int,
    val day: Int,        // 0 = Пн ... 6 = Вс
    val time: String,    // начало, например "10:15"
    val subject: String,
    val room: String,
    val date: Long = 0L, // 0 = каждую неделю, иначе дата одного занятия
    val end: String = "", // пусто = считается автоматически от режима
    val teacher: String = "",
    val lessonType: String = "",
    val source: String = "manual" // manual или asu
)

// Показывается ли занятие в этот день (dayIndex) и эту дату (dateMillis)
fun isOnDate(lesson: Lesson, dayIndex: Int, dateMillis: Long): Boolean =
    if (lesson.date == 0L) lesson.day == dayIndex else lesson.date == dateMillis

// Время окончания: вручную заданное или начало + длительность по режиму
fun lessonEnd(lesson: Lesson, minutes: Int): String =
    lesson.end.ifBlank { endTime(lesson.time, minutes) }

// Ограничения длины полей
const val MAX_LESSON_SUBJECT = 40
const val MAX_LESSON_ROOM = 15
const val MAX_TASK_TITLE = 60
const val MAX_TASK_SUBJECT = 40

val priorityNames = listOf("Низкий", "Средний", "Высокий")
val priorityColors = listOf(Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFFC62828))
val timeRegex = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

// Время окончания = начало + длительность занятия
fun endTime(start: String, minutes: Int): String {
    val parts = start.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return ""
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return ""
    val total = (h * 60 + m + minutes) % (24 * 60)
    return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
}

fun formatDate(millis: Long): String {
    val f = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    f.timeZone = TimeZone.getTimeZone("UTC")
    return f.format(Date(millis))
}

fun todayUtc(): Long = System.currentTimeMillis() / 86_400_000L * 86_400_000L

fun isOverdue(task: Task): Boolean =
    !task.done && task.deadline != 0L && task.deadline < todayUtc()

// Номер сегодняшнего дня: 0 = Пн ... 6 = Вс
fun todayIndex(): Int {
    val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) // Вс = 1, Пн = 2
    return (dow + 5) % 7
}

// Номер дня по местному времени (для серии и статистики)
fun localDay(millis: Long): Long =
    (millis + TimeZone.getDefault().getOffset(millis)) / 86_400_000L

// Сколько дней подряд выполнялось хотя бы одно задание
fun streak(tasks: List<Task>): Int {
    val days = tasks.filter { it.done && it.doneAt != 0L }.map { localDay(it.doneAt) }.toSet()
    var d = localDay(System.currentTimeMillis())
    if (d !in days) d -= 1   // сегодня ещё не выполнял: серия считается со вчера
    var n = 0
    while (d in days) {
        n++
        d--
    }
    return n
}
