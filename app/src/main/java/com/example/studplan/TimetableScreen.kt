package com.example.studplan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import android.webkit.WebSettings
import android.util.Log
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.os.Handler
import android.os.Looper
import org.json.JSONTokener
import com.example.studplan.ui.theme.AppTheme
import com.example.studplan.ui.theme.Manrope
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch

private const val DAY_MS = 86_400_000L

private fun utcCal(millis: Long): Calendar =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }

private fun dayOfMonth(millis: Long): Int = utcCal(millis).get(Calendar.DAY_OF_MONTH)

// "Октябрь, неделя 3"
private fun weekTitle(monday: Long): String {
    val thursday = monday + 3 * DAY_MS
    val f = SimpleDateFormat("LLLL", Locale("ru"))
    f.timeZone = TimeZone.getTimeZone("UTC")
    val month = f.format(Date(thursday)).replaceFirstChar { it.uppercase() }
    val week = (dayOfMonth(thursday) - 1) / 7 + 1
    return "$month, неделя $week"
}

// "19 – 25 октября 2026", "28 окт – 3 нояб 2026" или на стыке годов "28 дек 2026 – 3 янв 2027"
private fun rangeText(monday: Long): String {
    val sunday = monday + 6 * DAY_MS
    val mc = utcCal(monday)
    val sc = utcCal(sunday)
    val yearEnd = sc.get(Calendar.YEAR)
    return when {
        mc.get(Calendar.YEAR) != yearEnd ->
            "${shortDate(monday)} ${mc.get(Calendar.YEAR)} – ${shortDate(sunday)} $yearEnd"
        mc.get(Calendar.MONTH) == sc.get(Calendar.MONTH) -> {
            val f = SimpleDateFormat("d MMMM", Locale("ru"))
            f.timeZone = TimeZone.getTimeZone("UTC")
            "${dayOfMonth(monday)} – ${f.format(Date(sunday))} $yearEnd"
        }
        else -> "${shortDate(monday)} – ${shortDate(sunday)} $yearEnd"
    }
}

// "1 ЗАНЯТИЕ", "2 ЗАНЯТИЯ", "5 ЗАНЯТИЙ"
private fun countLabel(n: Int): String {
    val word = when {
        n % 100 in 11..14 -> "ЗАНЯТИЙ"
        n % 10 == 1 -> "ЗАНЯТИЕ"
        n % 10 in 2..4 -> "ЗАНЯТИЯ"
        else -> "ЗАНЯТИЙ"
    }
    return "$n $word"
}

// ---------- Иконки, нарисованные кодом (стиль Lucide) ----------

private val coffeePaths = listOf(
    "M10 2v2",
    "M14 2v2",
    "M16 8a1 1 0 0 1 1 1v8a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V9a1 1 0 0 1 1-1h14a4 4 0 1 1 0 8h-1",
    "M6 2v2"
)

private val sparklesPaths = listOf(
    "M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a0.5 0.5 0 0 1 0 -0.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a0.5 0.5 0 0 1 0.963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a0.5 0.5 0 0 1 0 0.964L15.5 14.063a2 2 0 0 0 -1.437 1.437l-1.582 6.135a0.5 0.5 0 0 1 -0.963 0z",
    "M20 3v4",
    "M22 5h-4",
    "M4 17v2",
    "M5 18H3"
)

@Composable
private fun LineIcon(paths: List<String>, tint: Color, iconSize: Dp) {
    val parsed: List<Path> = remember(paths) {
        paths.map { PathParser().parsePathString(it).toPath() }
    }
    Canvas(modifier = Modifier.size(iconSize)) {
        val k = size.minDimension / 24f
        scale(k, pivot = Offset.Zero) {
            parsed.forEach { p ->
                drawPath(
                    p, tint,
                    style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

// Экран "Свободный день"
@Composable
private fun EmptyDay() {
    val c = AppTheme.colors
    val shadowColor = Color(0xFF111B28).copy(alpha = 0.12f)
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(c.primarySoft),
                contentAlignment = Alignment.Center
            ) {
                LineIcon(coffeePaths, c.primary, 44.dp)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 8.dp)
                    .size(32.dp)
                    .shadow(3.dp, CircleShape, ambientColor = shadowColor, spotColor = shadowColor)
                    .clip(CircleShape)
                    .background(c.card),
                contentAlignment = Alignment.Center
            ) {
                LineIcon(sparklesPaths, c.primary, 16.dp)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Свободный день!",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Занятий нет — время отдохнуть, повторить материал или заняться своими делами.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.mutedForeground,
            textAlign = TextAlign.Center
        )
    }
}

// Круглая кнопка со стрелкой для переключения недель
@Composable
private fun NavCircle(left: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shadowColor = Color(0xFF111B28).copy(alpha = 0.10f)
    Box(
        modifier = Modifier
            .size(36.dp)
            .shadow(2.dp, CircleShape, ambientColor = shadowColor, spotColor = shadowColor)
            .clip(CircleShape)
            .background(c.card)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val tint = c.foreground
        Canvas(modifier = Modifier.size(14.dp)) {
            val p = Path().apply {
                if (left) {
                    moveTo(size.width * 0.65f, size.height * 0.15f)
                    lineTo(size.width * 0.30f, size.height * 0.50f)
                    lineTo(size.width * 0.65f, size.height * 0.85f)
                } else {
                    moveTo(size.width * 0.35f, size.height * 0.15f)
                    lineTo(size.width * 0.70f, size.height * 0.50f)
                    lineTo(size.width * 0.35f, size.height * 0.85f)
                }
            }
            drawPath(
                p, tint,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
private fun DayPill(
    label: String,
    date: Int,
    selected: Boolean,
    hasLessons: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val shadowColor = Color(0xFF111B28).copy(alpha = 0.08f)
    Column(
        modifier = modifier
            .shadow(2.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
            .clip(shape)
            .background(if (selected) c.primary else c.card)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 0.sp
            ),
            color = if (selected) c.onPrimary.copy(alpha = 0.85f) else c.mutedForeground
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "$date",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 16.sp,
                lineHeight = 20.sp
            ),
            color = if (selected) c.onPrimary else c.foreground
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(
                    when {
                        !hasLessons -> Color.Transparent
                        selected -> c.onPrimary
                        else -> c.primary
                    }
                )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    lessons: MutableList<Lesson>,
    subjects: List<String>,
    mode: UserMode,
    lastAsuSync: Long,
    onAsuSync: (Long) -> Unit
) {
    val c = AppTheme.colors
    val days = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    var selectedDay by rememberSaveable { mutableIntStateOf(todayIndex()) }
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var showDialog by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Lesson?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("") }
    var showWebImport by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Понедельник показанной недели (полночь по UTC, как у выбора даты)
    val currentMonday = (localDay(System.currentTimeMillis()) - todayIndex()) * DAY_MS
    val monday = currentMonday + weekOffset * 7L * DAY_MS
    val selectedDate = monday + selectedDay * DAY_MS

    val dayLessons = lessons
        .filter { isOnDate(it, selectedDay, selectedDate) }
        .sortedBy { it.time }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp)
        ) {
            // Заголовок и плашка "Сегодня"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        weekTitle(monday),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.mutedForeground
                    )
                    Text("Расписание", style = MaterialTheme.typography.headlineLarge)
                }
                if (weekOffset != 0) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(c.primarySoft)
                            .clickable {
                                weekOffset = 0
                                selectedDay = todayIndex()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "Сегодня",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.primary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    buildString {
                        append("Группа ${AsuTimetableRepository.GROUP_NAME}")
                        if (lastAsuSync != 0L) {
                            append(" · Обновлено ${SimpleDateFormat("HH:mm · d MMM", Locale("ru")).format(Date(lastAsuSync))}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.mutedForeground,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    enabled = !syncing,
                    onClick = {
                        if (!syncing) {
                            syncMessage = ""
                            syncing = true
                            showWebImport = true
                        }
                    }
                ) {
                    if (syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = c.primary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (syncing) "Загрузка" else "Обновить")
                }
            }
            if (syncMessage.isNotBlank()) {
                Text(
                    syncMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (syncMessage.startsWith("Не удалось")) c.danger else c.success,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Переключатель недель: нажатие на диапазон открывает выбор даты
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavCircle(left = true) { weekOffset-- }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showJump = true }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        rangeText(monday),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_calendar_days),
                        contentDescription = "Перейти к дате",
                        tint = c.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                NavCircle(left = false) { weekOffset++ }
            }

            Spacer(Modifier.height(12.dp))

            // Полоска дней недели
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEachIndexed { index, name ->
                    val date = monday + index * DAY_MS
                    DayPill(
                        label = name,
                        date = dayOfMonth(date),
                        selected = selectedDay == index,
                        hasLessons = lessons.any { isOnDate(it, index, date) },
                        modifier = Modifier.weight(1f),
                        onClick = { selectedDay = index }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Счётчик занятий
            if (dayLessons.isNotEmpty()) {
                SectionLabel(countLabel(dayLessons.size))
            }

            Spacer(Modifier.height(10.dp))

            if (dayLessons.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyDay()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(dayLessons, key = { it.id }) { lesson ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Время слева
                            Column(
                                modifier = Modifier
                                    .width(56.dp)
                                    .padding(top = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    lesson.time,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    lessonEnd(lesson, mode.lessonMinutes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.mutedForeground
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            // Карточка занятия
                            AppCard(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable {
                                            editing = lesson
                                            showDialog = true
                                        }
                                        .padding(16.dp)
                                        .height(IntrinsicSize.Min)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(c.primary)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            lesson.subject + if (lesson.lessonType.isNotBlank()) " · ${lesson.lessonType}" else "",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        if (lesson.teacher.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                lesson.teacher,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = c.mutedForeground
                                            )
                                        }
                                        if (lesson.room.isNotBlank()) {
                                            Spacer(Modifier.height(6.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_map_pin),
                                                    contentDescription = null,
                                                    tint = c.mutedForeground,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    lesson.room,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = c.mutedForeground
                                                )
                                            }
                                        }
                                        // Плашка только у постоянных занятий
                                        if (lesson.date == 0L) {
                                            Spacer(Modifier.height(8.dp))
                                            Chip(
                                                everyDayLabels[lesson.day].lowercase(),
                                                c.mutedForeground,
                                                c.muted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Кнопка "+"
        val fabShape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(56.dp)
                .shadow(
                    10.dp, fabShape,
                    ambientColor = c.primary.copy(alpha = 0.35f),
                    spotColor = c.primary.copy(alpha = 0.35f)
                )
                .clip(fabShape)
                .background(c.primary)
                .clickable {
                    editing = null
                    showDialog = true
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_plus),
                contentDescription = "Добавить занятие",
                tint = c.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    if (showDialog) {
        val current = editing
        LessonSheet(
            initial = current,
            dayIndex = current?.day ?: selectedDay,
            dayDate = current?.date?.takeIf { it != 0L } ?: selectedDate,
            mode = mode,
            subjects = subjects,
            onSave = { time, subject, room, date, end ->
                if (current == null) {
                    val newId = (lessons.maxOfOrNull { it.id } ?: 0) + 1
                    lessons.add(Lesson(newId, selectedDay, time, subject, room, date, end))
                } else {
                    val i = lessons.indexOfFirst { it.id == current.id }
                    if (i >= 0) {
                        lessons[i] = current.copy(
                            time = time,
                            subject = subject,
                            room = room,
                            date = date,
                            end = end
                        )
                    }
                }
                showDialog = false
                editing = null
            },
            onDelete = if (current == null) null else {
                {
                    lessons.removeAll { it.id == current.id }
                    showDialog = false
                    editing = null
                }
            },
            onDismiss = {
                showDialog = false
                editing = null
            }
        )
    }

    if (showWebImport) {
        AsuWebViewImport(
            onImported = { downloaded ->
                syncing = false
                showWebImport = false
                if (downloaded.isEmpty()) {
                    syncMessage = "На странице не найдено занятий"
                } else {
                    lessons.removeAll { it.source == "asu" || it.source == "sample" }
                    lessons.addAll(downloaded)
                    onAsuSync(System.currentTimeMillis())
                    syncMessage = "Загружено занятий: ${downloaded.size}"
                }
            },
            onError = { message ->
                syncing = false
                syncMessage = message
                showWebImport = false
            },
            onStatus = { status -> syncMessage = status },
            onClose = {
                syncing = false
                showWebImport = false
            }
        )
    }



    // Быстрый переход к любой дате (например, в следующий год)
    if (showJump) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
        DatePickerDialog(
            onDismissRequest = { showJump = false },
            confirmButton = {
                TextButton(onClick = {
                    val picked = state.selectedDateMillis
                    if (picked != null) {
                        val d = picked / DAY_MS
                        val dayIdx = ((d + 3) % 7).toInt()   // 0 = Пн ... 6 = Вс
                        val pickedMonday = (d - dayIdx) * DAY_MS
                        weekOffset = ((pickedMonday - currentMonday) / (7L * DAY_MS)).toInt()
                        selectedDay = dayIdx
                    }
                    showJump = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showJump = false }) { Text("Отмена") }
            }
        ) {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme,
                shapes = MaterialTheme.shapes,
                typography = MaterialTheme.typography.copy(
                    headlineLarge = TextStyle(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        lineHeight = 32.sp
                    ),
                    headlineMedium = TextStyle(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        lineHeight = 28.sp
                    ),
                    headlineSmall = TextStyle(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        lineHeight = 24.sp
                    ),
                    titleLarge = TextStyle(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    ),
                    titleMedium = TextStyle(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    titleSmall = TextStyle(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    bodyLarge = TextStyle(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Normal,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    ),
                    bodyMedium = TextStyle(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    bodySmall = TextStyle(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    labelLarge = TextStyle(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    labelMedium = TextStyle(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    labelSmall = TextStyle(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                )
            ) {
                DatePicker(state = state)
            }
        }
    }
}

private const val TAG = "AsuImport"

private const val READY_JS = """
(function(){
  var c = document.querySelector('#timetable-container');
  if (!c) return 'wait';
  if (c.querySelector('.schedule_table-body-rows_group')) return 'rows';
  var t = document.body ? document.body.innerText : '';
  if (t.indexOf('Нет занятий') >= 0) return 'none';
  if (c.querySelector('.schedule_table-body')) return 'empty';
  return 'wait';
})()
"""

@Composable
private fun AsuWebViewImport(
    onImported: (List<Lesson>) -> Unit,
    onError: (String) -> Unit,
    onStatus: (String) -> Unit,
    onClose: () -> Unit
) {
    val weeks = remember { AsuTimetableRepository.upcomingWeekUrls(count = 20) }
    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { context ->
            val density = context.resources.displayMetrics.density
            val container = FrameLayout(context).apply {
                alpha = 0f            // невидимый, но не GONE
                clipChildren = false
            }
            val webView = WebView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (360 * density).toInt(),
                    (640 * density).toInt()
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = false
                settings.blockNetworkImage = true
                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
                container.addView(this)
            }
            val handler = Handler(Looper.getMainLooper())
            val allLessons = mutableListOf<Lesson>()
            val importStartedAt = System.currentTimeMillis()
            var weekStartedAt = 0L
            var finished = false
            var weekIndex = 0
            var emptyInRow = 0
            var retries = 0
            var token = 0          // номер текущей загрузки страницы
            var handledToken = -1  // загрузка, которую уже обрабатываем

            fun elapsedWeek() = System.currentTimeMillis() - weekStartedAt

            fun finishError(message: String) {
                if (finished) return
                finished = true
                webView.stopLoading()
                Log.d(TAG, "ошибка: $message")
                onError(message)
            }

            fun finishSuccess() {
                if (finished) return
                finished = true
                webView.stopLoading()
                val result = allLessons.distinctBy { it.id }
                Log.d(
                    TAG,
                    "готово: недель прочитано $weekIndex, занятий ${result.size}, " +
                            "всего ${System.currentTimeMillis() - importStartedAt} мс"
                )
                onImported(result)
            }

            lateinit var startWeek: () -> Unit

            fun nextWeek() {
                weekIndex++
                retries = 0
                startWeek()
            }

            fun onEmptyWeek() {
                emptyInRow++
                Log.d(TAG, "неделя ${weekIndex + 1}: пусто ($emptyInRow подряд), ${elapsedWeek()} мс")
                if (emptyInRow >= 2) finishSuccess() else nextWeek()
            }

            fun readWhenReady(view: WebView, myToken: Int, attempt: Int) {
                if (finished || myToken != token) return
                if (attempt >= 100) {   // 100 x 100 мс = 10 секунд
                    if (retries < 1) {
                        retries++
                        Log.d(TAG, "неделя ${weekIndex + 1}: таймаут, повтор")
                        startWeek()
                    } else {
                        finishError(
                            "Не удалось загрузить расписание (неделя ${weekIndex + 1}). " +
                                    "Проверь интернет и нажми «Обновить» ещё раз"
                        )
                    }
                    return
                }
                view.evaluateJavascript(READY_JS) { raw ->
                    if (finished || myToken != token) return@evaluateJavascript
                    val state = raw?.trim('"') ?: "wait"
                    when {
                        state == "rows" ->
                            view.evaluateJavascript("document.documentElement.outerHTML") { encoded ->
                                if (finished || myToken != token) return@evaluateJavascript
                                val result = runCatching {
                                    val html = JSONTokener(encoded).nextValue() as String
                                    AsuTimetableRepository.parseHtml(html)
                                }
                                result.onSuccess { list ->
                                    Log.d(
                                        TAG,
                                        "неделя ${weekIndex + 1}: занятий ${list.size}, ${elapsedWeek()} мс"
                                    )
                                    if (list.isEmpty()) {
                                        onEmptyWeek()
                                    } else {
                                        emptyInRow = 0
                                        allLessons.addAll(list)
                                        nextWeek()
                                    }
                                }.onFailure { finishError("Ошибка чтения HTML: ${it.message}") }
                            }
                        // явная надпись "Нет занятий" подтверждаем 1,2 секунды
                        state == "none" && attempt >= 12 -> onEmptyWeek()
                        // неясный пустой вид ждём дольше: возможно, страница ещё рисуется
                        state == "empty" && attempt >= 30 -> onEmptyWeek()
                        else -> handler.postDelayed(
                            { readWhenReady(view, myToken, attempt + 1) },
                            if (attempt == 0) 0L else 100L
                        )
                    }
                }
            }

            startWeek = start@{
                if (finished) return@start
                if (weekIndex >= weeks.size) {
                    finishSuccess()
                    return@start
                }
                token++
                weekStartedAt = System.currentTimeMillis()
                onStatus("Загружаем расписание")
                Log.d(TAG, "неделя ${weekIndex + 1}/${weeks.size}: ${weeks[weekIndex]}")
                webView.loadUrl(weeks[weekIndex])
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // одну загрузку обрабатываем только один раз
                    if (finished || handledToken == token) return
                    handledToken = token
                    Log.d(TAG, "неделя ${weekIndex + 1}: страница загружена за ${elapsedWeek()} мс")
                    readWhenReady(view, token, 0)
                }

                override fun onReceivedError(
                    view: WebView,
                    errorCode: Int,
                    description: String,
                    failingUrl: String
                ) {
                    finishError("Ошибка WebView: $description")
                }
            }
            startWeek()
            container
        },
        update = { container ->
            (container.getChildAt(0) as? WebView)?.onResume()
        }
    )
}