package com.example.studplan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Downloads and parses the public timetable HTML for ASU group 4.301-1. */
object AsuTimetableRepository {
    const val GROUP_NAME = "4.301-1"
    private const val TIMETABLE_URL =
        "https://www.asu.ru/timetable/students/16/2129441335/"
    private const val SOURCE = "asu"
    private const val ASU_TIME_ZONE = "Asia/Krasnoyarsk"

    fun weekUrl(monday: Calendar): String {
        val format = SimpleDateFormat("yyyyMMdd", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone(ASU_TIME_ZONE)
        }
        val sunday = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 6) }
        return "https://www.asu.ru/timetable/students/16/2129441335/?date=" +
                "${format.format(monday.time)}-${format.format(sunday.time)}"
    }

    fun upcomingWeekUrls(count: Int = 52): List<String> {
        val monday = Calendar.getInstance(TimeZone.getTimeZone(ASU_TIME_ZONE)).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
        }
        return (0 until count).map { offset ->
            (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, offset * 7) }
        }.map(::weekUrl)
    }

    suspend fun downloadLessons(): List<Lesson> = withContext(Dispatchers.IO) {
        val connection = (URL(TIMETABLE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            // Короткое ожидание: пользователь сразу получает понятную ошибку,
            // если сайт недоступен из сети эмулятора.
            connectTimeout = 10_000
            readTimeout = 10_000
            useCaches = false
            setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) StudPlan/1.0")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Сайт АГУ ответил кодом $code")
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseHtml(html)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseCalendar(text: String): List<Lesson> {
        val unfolded = mutableListOf<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && unfolded.isNotEmpty()) {
                unfolded[unfolded.lastIndex] += line.drop(1)
            } else {
                unfolded += line
            }
        }

        val lessons = mutableListOf<Lesson>()
        var event: MutableMap<String, String>? = null
        unfolded.forEach { line ->
            when (line) {
                "BEGIN:VEVENT" -> event = linkedMapOf()
                "END:VEVENT" -> {
                    event?.let { fields -> parseEvent(fields)?.let(lessons::add) }
                    event = null
                }
                else -> {
                    val current = event ?: return@forEach
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val property = line.substring(0, colon).substringBefore(';').uppercase(Locale.ROOT)
                        current[property] = line.substring(colon + 1)
                    }
                }
            }
        }
        return lessons.distinctBy { it.id }.sortedWith(compareBy<Lesson> { it.date }.thenBy { it.time })
    }

    /** Parses the schedule_table markup returned by the normal timetable page. */
    internal fun parseHtml(html: String): List<Lesson> {
        val source = html.replace("\r", "")
        val year = Regex("selected_date-box-cell\\\">(20\\d{2})<")
            .find(source)?.groupValues?.get(1)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
        val groupMarker = Regex(
            "<div\\s+class=\\\"schedule_table-body-rows_group\\\"[^>]*>",
            RegexOption.IGNORE_CASE
        )
        val groupStarts = groupMarker.findAll(source).map { it.range.first }.toList()
        val lessons = mutableListOf<Lesson>()
        groupStarts.forEachIndexed { groupIndex, groupStart ->
            val groupEnd = groupStarts.getOrNull(groupIndex + 1) ?: source.indexOf("</div><!-- .schedule_table-body-rows_group -->", groupStart)
                .takeIf { it >= 0 } ?: source.length
            val group = source.substring(groupStart, groupEnd)
            val dateText = cellText(group, "date")
            val dateMatch = Regex("(\\d{1,2})\\.(\\d{1,2})").find(dateText) ?: return@forEachIndexed
            val day = dateMatch.groupValues[1].toInt()
            val month = dateMatch.groupValues[2].toInt()
            val date = Calendar.getInstance(TimeZone.getTimeZone(ASU_TIME_ZONE)).apply {
                clear()
                set(year, month - 1, day)
            }
            val dayDate = utcDateMillis(date)
            val rowStarts = Regex("<div\\s+class=\\\"schedule_table-body-row(?:\\s|\\\")", RegexOption.IGNORE_CASE)
                .findAll(group).map { it.range.first }.toList()
            rowStarts.forEachIndexed { index, start ->
                val endIndex = rowStarts.getOrNull(index + 1) ?: group.length
                val row = group.substring(start, endIndex)
                val time = cellText(row, "time").replace(Regex("\\s+"), " ").trim()
                if (!Regex("^\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2}$").matches(time)) return@forEachIndexed
                val number = cellText(row, "num")
                val subjectRaw = cellText(row, "subject")
                val lecturer = cellText(row, "lecturer")
                val room = cellText(row, "room")
                if (subjectRaw.isBlank()) return@forEachIndexed
                val subject = Regex("^(лек\\.|лаб\\.|пр\\.з\\.)\\s*", RegexOption.IGNORE_CASE)
                    .find(subjectRaw)?.let { subjectRaw.removePrefix(it.value).trim() } ?: subjectRaw
                val type = Regex("^(лек\\.|лаб\\.|пр\\.з\\.)", RegexOption.IGNORE_CASE)
                    .find(subjectRaw)?.value.orEmpty()
                val times = time.split(Regex("\\s*-\\s*"))
                val uid = "$dayDate|$number|$time|$subject|$room"
                lessons += Lesson(
                    id = (uid.hashCode() and Int.MAX_VALUE).coerceAtLeast(1),
                    day = ((date.get(Calendar.DAY_OF_WEEK) + 5) % 7),
                    time = times[0],
                    subject = subject,
                    room = room,
                    date = dayDate,
                    end = times.getOrElse(1) { "" },
                    teacher = lecturer,
                    lessonType = type,
                    source = SOURCE
                )
            }
        }
        return lessons.distinctBy { it.id }.sortedWith(compareBy<Lesson> { it.date }.thenBy { it.time })
    }

    private fun cellText(block: String, type: String): String {
        val pattern = Regex(
            "<div[^>]*data-type=\\\"$type\\\"[^>]*>(.*?)</div>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        return pattern.find(block)?.groupValues?.get(1)
            ?.replace(Regex("<[^>]+>"), " ")
            ?.replace("&nbsp;", " ", ignoreCase = true)
            ?.replace("&amp;", "&", ignoreCase = true)
            ?.replace("&quot;", "\"", ignoreCase = true)
            ?.replace("&#39;", "'", ignoreCase = true)
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
    }

    private fun parseEvent(fields: Map<String, String>): Lesson? {
        val start = fields["DTSTART"] ?: return null
        val end = fields["DTEND"] ?: return null
        val startDate = parseDateTime(start) ?: return null
        val endDate = parseDateTime(end) ?: return null
        val summary = unescape(fields["SUMMARY"].orEmpty()).trim()
        if (summary.isBlank()) return null

        val location = unescape(fields["LOCATION"].orEmpty()).trim()
        val description = unescape(fields["DESCRIPTION"].orEmpty()).trim()
        val timePrefix = Regex("^(\\d{1,2}:\\d{2})\\s*").find(summary)?.groupValues?.get(1)
        val body = if (timePrefix != null) summary.removePrefix(timePrefix).trim() else summary
        val typeMatch = Regex("^(лек\\.|лаб\\.|пр\\.з\\.)\\s*", RegexOption.IGNORE_CASE).find(body)
        val lessonType = typeMatch?.groupValues?.get(1).orEmpty()
        val withoutType = if (typeMatch != null) body.drop(typeMatch.value.length).trim() else body
        val subject = if (location.isNotBlank()) {
            withoutType.replace(Regex("\\s*\\(${Regex.escape(location)}\\)\\s*$"), "").trim()
        } else withoutType
        val date = utcDateMillis(startDate)
        val uid = fields["UID"].orEmpty()
        val id = (uid.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)

        return Lesson(
            id = id,
            day = ((startDate.get(Calendar.DAY_OF_WEEK) + 5) % 7),
            time = formatTime(startDate),
            subject = subject.ifBlank { body },
            room = location,
            date = date,
            end = formatTime(endDate),
            teacher = description,
            lessonType = lessonType,
            source = SOURCE
        )
    }

    private fun parseDateTime(propertyValue: String): Calendar? {
        val value = propertyValue.substringAfter(':', propertyValue)
        val pattern = if (value.length >= 15 && value[8] == 'T') "yyyyMMdd'T'HHmmss" else "yyyyMMdd'T'HHmm"
        return runCatching {
            val format = SimpleDateFormat(pattern, Locale.ROOT).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone(ASU_TIME_ZONE)
            }
            Calendar.getInstance(TimeZone.getTimeZone(ASU_TIME_ZONE)).apply {
                timeInMillis = format.parse(value)?.time ?: return null
            }
        }.getOrNull()
    }

    private fun utcDateMillis(source: Calendar): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).run {
        clear()
        set(source.get(Calendar.YEAR), source.get(Calendar.MONTH), source.get(Calendar.DAY_OF_MONTH))
        timeInMillis
    }

    private fun formatTime(calendar: Calendar): String = String.format(
        Locale.ROOT,
        "%02d:%02d",
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE)
    )

    private fun unescape(value: String): String = value
        .replace("\\n", "\n", ignoreCase = true)
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")
}
