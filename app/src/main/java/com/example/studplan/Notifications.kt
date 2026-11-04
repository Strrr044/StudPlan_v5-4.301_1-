package com.example.studplan

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.TimeZone

data class NotifSettings(
    val enabled: Boolean = false,          // общий выключатель
    val dayBefore: Boolean = true,         // напоминать за день до срока
    val dayBeforeTime: String = "18:00",
    val sameDay: Boolean = true,           // напоминать в день срока
    val sameDayTime: String = "09:00",
    val lessons: Boolean = true,           // напоминать о занятиях
    val lessonBefore: Int = 15             // за сколько минут: 10 / 15 / 30
)

private class Planned(val code: Int, val at: Long, val title: String, val text: String)

private const val DAY = 86_400_000L

object Notifier {
    const val CHANNEL_ID = "studplan_reminders"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Напоминания", NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Дедлайны и занятия"
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

    // Показать уведомление прямо сейчас
    fun show(context: Context, id: Int, title: String, text: String) {
        createChannel(context)
        if (!hasPermission(context)) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_circle_check)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // нет разрешения, ничего не делаем
        }
    }

    private fun pending(context: Context, code: Int, title: String?, text: String?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        if (title != null) {
            intent.putExtra("title", title)
            intent.putExtra("text", text ?: "")
            intent.putExtra("code", code)
        }
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Отменяет все прежние напоминания и ставит новые по текущим данным и настройкам
    fun reschedule(
        context: Context,
        tasks: List<Task>,
        lessons: List<Lesson>,
        settings: NotifSettings
    ) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val state = app.getSharedPreferences("notif_state", Context.MODE_PRIVATE)

        state.getStringSet("codes", emptySet())?.toList()?.forEach {
            am.cancel(pending(app, it.toInt(), null, null))
        }

        val newCodes = mutableSetOf<String>()
        if (settings.enabled && hasPermission(app)) {
            for (p in plan(tasks, lessons, settings)) {
                am.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, p.at, pending(app, p.code, p.title, p.text)
                )
                newCodes.add(p.code.toString())
            }
        }
        state.edit().putStringSet("codes", newCodes).apply()
    }

    private fun code(kind: Int, id: Int, offset: Int) =
        kind * 10_000_000 + (id % 100_000) * 100 + offset

    // Дата хранится как полночь UTC; собираем местное время нужного дня
    private fun localMillis(dateUtc: Long, dayShift: Int, hhmm: String): Long {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateUtc }
        val parts = hhmm.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(
            u.get(Calendar.YEAR), u.get(Calendar.MONTH),
            u.get(Calendar.DAY_OF_MONTH) + dayShift, h, m, 0
        )
        return cal.timeInMillis
    }

    private fun plan(
        tasks: List<Task>,
        lessons: List<Lesson>,
        s: NotifSettings
    ): List<Planned> {
        val now = System.currentTimeMillis()
        val out = mutableListOf<Planned>()

        // Дедлайны: невыполненные задания со сроком, на ближайшие 60 дней
        for (t in tasks) {
            if (t.done || t.deadline == 0L) continue
            val subject = if (t.subject.isBlank()) "" else " · ${t.subject}"
            if (s.dayBefore) {
                val at = localMillis(t.deadline, -1, s.dayBeforeTime)
                if (at > now && at < now + 60 * DAY) {
                    out += Planned(code(1, t.id, 0), at, "Завтра срок сдачи", t.title + subject)
                }
            }
            if (s.sameDay) {
                val at = localMillis(t.deadline, 0, s.sameDayTime)
                if (at > now && at < now + 60 * DAY) {
                    out += Planned(code(2, t.id, 0), at, "Сегодня срок сдачи", t.title + subject)
                }
            }
        }

        // Занятия и сводка: на ближайшие 14 дней
        val today = localDay(now) * DAY
        for (off in 0 until 14) {
            val date = today + off * DAY
            val dayIdx = (((date / DAY) + 3) % 7).toInt()   // 0 = Пн ... 6 = Вс
            val dayLessons = lessons.filter { isOnDate(it, dayIdx, date) }

            if (s.lessons) {
                for (l in dayLessons) {
                    val at = localMillis(date, 0, l.time) - s.lessonBefore * 60_000L
                    if (at > now) {
                        val room = if (l.room.isBlank()) "" else ", ${l.room}"
                        out += Planned(
                            code(3, l.id, off), at,
                            "Через ${s.lessonBefore} мин: ${l.subject}",
                            "Начало в ${l.time}$room"
                        )
                    }
                }
            }


        }
        return out
    }
}

// Сюда приходит сработавший будильник
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        val text = intent.getStringExtra("text") ?: ""
        val code = intent.getIntExtra("code", 0)
        Notifier.show(context, code, title, text)
    }
}

// После перезагрузки телефона будильники пропадают, ставим заново
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val storage = Storage(context)
            Notifier.reschedule(
                context, storage.loadTasks(), storage.loadLessons(), storage.loadNotif()
            )
        }
    }
}