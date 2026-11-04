package com.example.studplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.studplan.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------- Общие помощники (используются и на других экранах) ----------

// "15 окт"
fun shortDate(millis: Long): String {
    val f = SimpleDateFormat("d MMM", Locale("ru"))
    f.timeZone = TimeZone.getTimeZone("UTC")
    return f.format(Date(millis)).replace(".", "")
}

// 1 день, 2 дня, 5 дней
fun daysWord(n: Int): String = when {
    n % 100 in 11..14 -> "дней"
    n % 10 == 1 -> "день"
    n % 10 in 2..4 -> "дня"
    else -> "дней"
}

// Белая карточка со скруглением и мягкой тенью
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val shadowColor = Color(0xFF111B28).copy(alpha = 0.10f)
    Column(
        modifier = modifier
            .shadow(3.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
            .background(AppTheme.colors.card, shape),
        content = content
    )
}

// Маленькая плашка: "Высокий", "просрочено · 15 окт"
@Composable
fun Chip(text: String, fg: Color, bg: Color, iconRes: Int? = null) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
fun PriorityChip(priority: Int) {
    val c = AppTheme.colors
    val (fg, bg) = when (priority) {
        2 -> c.danger to c.dangerSoft
        1 -> c.warning to c.warningSoft
        else -> c.success to c.successSoft
    }
    Chip(priorityNames[priority], fg, bg)
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = AppTheme.colors.mutedForeground
    )
}

// ---------- Главный экран ----------

@Composable
fun HomeScreen(
    tasks: MutableList<Task>,
    lessons: List<Lesson>,
    mode: UserMode,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    notif: NotifSettings,
    onNotifChange: (NotifSettings) -> Unit
) {
    val c = AppTheme.colors
    var showNotif by remember { mutableStateOf(false) }
    if (showNotif) {
        NotificationsSheet(
            settings = notif,
            onChange = onNotifChange,
            onDismiss = { showNotif = false }
        )
    }
    val left = tasks.count { !it.done }
    val total = tasks.size
    val doneCount = total - left
    val share = if (total == 0) 0f else doneCount.toFloat() / total
    val streakDays = streak(tasks)
    val todayDate = localDay(System.currentTimeMillis()) * 86_400_000L
    val todayLessons = lessons
        .filter { isOnDate(it, todayIndex(), todayDate) }
        .sortedBy { it.time }
    val upcoming = tasks
        .filter { !it.done && it.deadline != 0L }
        .sortedBy { it.deadline }
        .take(3)
    val dateText = SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Заголовок и кнопка темы
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.mutedForeground
                )
                Text("Привет! 👋", style = MaterialTheme.typography.headlineLarge)
            }
            val bellShadow = Color(0xFF111B28).copy(alpha = 0.10f)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(3.dp, CircleShape, ambientColor = bellShadow, spotColor = bellShadow)
                    .clip(CircleShape)
                    .background(c.card)
                    .clickable { showNotif = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bell),
                    contentDescription = "Уведомления",
                    tint = if (notif.enabled) c.primary else c.foreground,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            val btnShadow = Color(0xFF111B28).copy(alpha = 0.10f)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(3.dp, CircleShape, ambientColor = btnShadow, spotColor = btnShadow)
                    .clip(CircleShape)
                    .background(c.card)
                    .clickable { onThemeChange(!darkTheme) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        if (darkTheme) R.drawable.ic_moon else R.drawable.ic_sun
                    ),
                    contentDescription = "Сменить тему",
                    tint = c.foreground,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Большая синяя карточка
        val bigShape = RoundedCornerShape(28.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    14.dp, bigShape,
                    ambientColor = c.primary.copy(alpha = 0.35f),
                    spotColor = c.primary.copy(alpha = 0.35f)
                )
                .background(
                    Brush.linearGradient(
                        listOf(c.primary, lerp(c.primary, Color.Black, 0.08f))
                    ),
                    bigShape
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    "Осталось\nзаданий",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.onPrimary.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f)
                )
                if (streakDays > 0) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(c.onPrimary.copy(alpha = 0.22f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_flame),
                            contentDescription = null,
                            tint = c.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "$streakDays ${daysWord(streakDays)} подряд",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.onPrimary
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("$left", style = MaterialTheme.typography.displayLarge, color = c.onPrimary)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(c.onPrimary.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(share.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(c.onPrimary)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Выполнено $doneCount из $total",
                style = MaterialTheme.typography.titleSmall,
                color = c.onPrimary
            )
        }

        // Занятия сегодня
        Spacer(Modifier.height(28.dp))
        SectionLabel("СЕГОДНЯ")
        Spacer(Modifier.height(10.dp))
        if (todayLessons.isEmpty()) {
            if (upcoming.isEmpty()) {
                // Когда день полностью свободен, показываем более выразительное
                // состояние, а не оставляем пользователя с двумя строками текста.
                EmptyHomeState()
            } else {
                Text(
                    "Сегодня занятий нет 🎉",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.mutedForeground
                )
            }
        } else {
            todayLessons.forEach { lesson ->
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
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
                            Text(lesson.subject, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_clock),
                                    contentDescription = null,
                                    tint = c.mutedForeground,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "${lesson.time}–${lessonEnd(lesson, mode.lessonMinutes)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.mutedForeground
                                )
                                if (lesson.room.isNotBlank()) {
                                    Spacer(Modifier.width(12.dp))
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
                        }
                    }
                }
            }
        }

        // Ближайшие дедлайны
        Spacer(Modifier.height(18.dp))
        SectionLabel("БЛИЖАЙШИЕ ДЕДЛАЙНЫ")
        Spacer(Modifier.height(10.dp))
        if (upcoming.isEmpty()) {
            if (todayLessons.isEmpty()) {
                EmptyDeadlineCard()
            } else {
                Text(
                    "Дедлайнов нет",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.mutedForeground
                )
            }
        } else {
            upcoming.forEach { task ->
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        // Круглая отметка "выполнено"
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .border(2.dp, c.border, CircleShape)
                                .clickable {
                                    val i = tasks.indexOfFirst { it.id == task.id }
                                    if (i >= 0) {
                                        tasks[i] = task.copy(
                                            done = true,
                                            doneAt = System.currentTimeMillis()
                                        )
                                    }
                                }
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium)
                            if (task.subject.isNotBlank()) {
                                Text(
                                    task.subject,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.mutedForeground
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PriorityChip(task.priority)
                                if (isOverdue(task)) {
                                    Chip(
                                        "просрочено · ${shortDate(task.deadline)}",
                                        c.danger, c.dangerSoft,
                                        R.drawable.ic_alert_triangle
                                    )
                                } else {
                                    Chip(
                                        "до ${shortDate(task.deadline)}",
                                        c.mutedForeground, c.muted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// Пустое состояние главного экрана в стиле пустого расписания.
@Composable
private fun EmptyHomeState() {
    val c = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.primarySoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_calendar_days),
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(38.dp)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 8.dp)
                    .size(28.dp)
                    .shadow(
                        3.dp,
                        CircleShape,
                        ambientColor = c.foreground.copy(alpha = 0.12f),
                        spotColor = c.foreground.copy(alpha = 0.12f)
                    )
                    .clip(CircleShape)
                    .background(c.card),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_plus),
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "Всё под контролем",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Сегодня занятий нет, а задания сданы —\nможно спокойно отдохнуть.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.mutedForeground,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyDeadlineCard() {
    val c = AppTheme.colors
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(c.primarySoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_circle_check),
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Дедлайнов нет", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Всё сдано вовремя",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.mutedForeground
                )
            }
        }
    }
}
