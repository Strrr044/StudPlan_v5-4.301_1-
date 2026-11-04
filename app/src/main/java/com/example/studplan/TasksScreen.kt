package com.example.studplan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.studplan.ui.theme.AppTheme

private val filterNames = listOf("Активные", "Готово", "Все")

// Круглая отметка выполнения
@Composable
private fun RoundCheck(checked: Boolean, onToggle: () -> Unit) {
    val c = AppTheme.colors
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (checked) c.primary else Color.Transparent)
            .border(2.dp, if (checked) c.primary else c.border, CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            val tick = c.onPrimary
            Canvas(modifier = Modifier.size(14.dp)) {
                val p = Path().apply {
                    moveTo(size.width * 0.15f, size.height * 0.55f)
                    lineTo(size.width * 0.40f, size.height * 0.80f)
                    lineTo(size.width * 0.88f, size.height * 0.22f)
                }
                drawPath(
                    p, tick,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

// Переключатель "Активные / Готово / Все"
@Composable
private fun FilterSwitch(selected: Int, onSelect: (Int) -> Unit) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.muted)
            .padding(4.dp)
    ) {
        filterNames.forEachIndexed { index, name ->
            val isSel = index == selected
            val shape = RoundedCornerShape(12.dp)
            val shadowColor = Color(0xFF111B28).copy(alpha = 0.10f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isSel) Modifier.shadow(
                            2.dp, shape,
                            ambientColor = shadowColor, spotColor = shadowColor
                        ) else Modifier
                    )
                    .clip(shape)
                    .background(if (isSel) c.card else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSel) c.foreground else c.mutedForeground
                )
            }
        }
    }
}

@Composable
fun TasksScreen(tasks: MutableList<Task>, subjects: List<String>) {
    val c = AppTheme.colors
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }

    val activeCount = tasks.count { !it.done }
    val shown = tasks
        .filter {
            when (filter) {
                0 -> !it.done
                1 -> it.done
                else -> true
            }
        }
        .sortedWith(compareBy<Task> { it.done }.thenByDescending { it.priority })

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
        ) {
            item {
                Column {
                    Row {
                        Text(
                            "$activeCount",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.primary
                        )
                        Text(
                            " активных",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.mutedForeground
                        )
                    }
                    Text("Задания", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(16.dp))
                    FilterSwitch(selected = filter, onSelect = { filter = it })
                    Spacer(Modifier.height(6.dp))
                }
            }

            items(shown, key = { it.id }) { task ->
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                editing = task
                                showDialog = true
                            }
                            .padding(16.dp)
                    ) {
                        RoundCheck(
                            checked = task.done,
                            onToggle = {
                                val i = tasks.indexOfFirst { t -> t.id == task.id }
                                if (i >= 0) {
                                    tasks[i] = task.copy(
                                        done = !task.done,
                                        doneAt = if (!task.done) System.currentTimeMillis() else 0L
                                    )
                                }
                            }
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                task.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (task.done) c.mutedForeground else c.foreground,
                                textDecoration = if (task.done) TextDecoration.LineThrough else null
                            )
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
                                if (task.deadline != 0L) {
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
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_trash),
                            contentDescription = "Удалить",
                            tint = c.mutedForeground,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { tasks.removeAll { t -> t.id == task.id } }
                        )
                    }
                }
            }
        }

        // Пустое состояние оформлено так же, как «Свободный день» в расписании.
        if (shown.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Заголовок и фильтры остаются сверху, пустое состояние центрируется ниже них.
                    .padding(top = 132.dp, bottom = 76.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    // Отдельная внешняя обёртка не обрезает декоративный значок.
                    // Увеличенный блок делает пустое состояние заметнее и лучше
                    // заполняет свободное пространство экрана.
                    Box(modifier = Modifier.padding(bottom = 10.dp, end = 10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .clip(RoundedCornerShape(34.dp))
                                .background(c.primarySoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(52.dp)) {
                                val strokeWidth = 2.5.dp.toPx()
                                drawCircle(
                                    color = c.primary,
                                    radius = size.minDimension * 0.39f,
                                    style = Stroke(width = strokeWidth)
                                )
                                val check = Path().apply {
                                    moveTo(size.width * 0.32f, size.height * 0.51f)
                                    lineTo(size.width * 0.45f, size.height * 0.64f)
                                    lineTo(size.width * 0.69f, size.height * 0.38f)
                                }
                                drawPath(
                                    path = check,
                                    color = c.primary,
                                    style = Stroke(
                                        width = strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 8.dp, y = 8.dp)
                                .size(36.dp)
                                .shadow(
                                    3.dp, CircleShape,
                                    ambientColor = Color(0xFF111B28).copy(alpha = 0.12f),
                                    spotColor = Color(0xFF111B28).copy(alpha = 0.12f)
                                )
                                .clip(CircleShape)
                                .background(c.card),
                            contentAlignment = Alignment.Center
                        ) {
                            // Маленькая искра, как на пустом экране расписания.
                            Canvas(modifier = Modifier.size(18.dp)) {
                                val w = size.width
                                val h = size.height
                                val sparkle = Path().apply {
                                    moveTo(w * 0.50f, h * 0.04f)
                                    cubicTo(w * 0.55f, h * 0.35f, w * 0.65f, h * 0.45f, w * 0.96f, h * 0.50f)
                                    cubicTo(w * 0.65f, h * 0.55f, w * 0.55f, h * 0.65f, w * 0.50f, h * 0.96f)
                                    cubicTo(w * 0.45f, h * 0.65f, w * 0.35f, h * 0.55f, w * 0.04f, h * 0.50f)
                                    cubicTo(w * 0.35f, h * 0.45f, w * 0.45f, h * 0.35f, w * 0.50f, h * 0.04f)
                                    close()
                                }
                                drawPath(sparkle, c.primary)
                            }
                        }
                    }
                    Spacer(Modifier.height(26.dp))
                    Text(
                        when (filter) {
                            0 -> "Можно выдохнуть"
                            1 -> "Всё впереди"
                            else -> "Пока здесь пусто"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.foreground,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when (filter) {
                            0 -> "Активных заданий пока нет.\nВремя для себя и новых идей."
                            1 -> "Выполненные задания появятся здесь."
                            else -> "Добавь первое задание, когда будешь готов."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.mutedForeground,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
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
                contentDescription = "Добавить задание",
                tint = c.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    if (showDialog) {
        val current = editing
        TaskSheet(
            initial = current,
            subjects = subjects,
            onSave = { title, subject, priority, deadline ->
                if (current == null) {
                    val newId = (tasks.maxOfOrNull { it.id } ?: 0) + 1
                    tasks.add(Task(newId, title, subject, false, priority, deadline))
                } else {
                    val i = tasks.indexOfFirst { it.id == current.id }
                    if (i >= 0) {
                        tasks[i] = current.copy(
                            title = title,
                            subject = subject,
                            priority = priority,
                            deadline = deadline
                        )
                    }
                }
                showDialog = false
                editing = null
            },
            onDismiss = {
                showDialog = false
                editing = null
            }
        )
    }
}
