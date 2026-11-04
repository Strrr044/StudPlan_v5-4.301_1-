package com.example.studplan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.studplan.ui.theme.AppTheme

private val dayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@Composable
private fun StatCard(value: Int, label: String, valueColor: Color, modifier: Modifier) {
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "$value",
                style = MaterialTheme.typography.headlineSmall,
                color = valueColor
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.mutedForeground,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun ProgressScreen(tasks: List<Task>) {
    val c = AppTheme.colors

    val total = tasks.size
    val done = tasks.count { it.done }
    val overdue = tasks.count { isOverdue(it) }
    val inWork = tasks.count { !it.done && !isOverdue(it) }
    val share = if (total == 0) 0f else done.toFloat() / total
    val percent = (share * 100).toInt()

    // Сколько заданий выполнено в каждый день текущей недели
    val today = todayIndex()
    val mondayDay = remember { localDay(System.currentTimeMillis()) - today }
    val counts = List(7) { i ->
        tasks.count { it.done && it.doneAt != 0L && localDay(it.doneAt) == mondayDay + i }
    }
    val maxCount = (counts.maxOrNull() ?: 0).coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            "Эта неделя",
            style = MaterialTheme.typography.bodyMedium,
            color = c.mutedForeground
        )
        Text("Прогресс", style = MaterialTheme.typography.headlineLarge)

        Spacer(Modifier.height(16.dp))

        // Кольцо с процентами
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                val track = c.muted
                val arc = c.primary
                Canvas(modifier = Modifier.size(190.dp)) {
                    val stroke = 16.dp.toPx()
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    drawArc(
                        color = track,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke)
                    )
                    if (share > 0f) {
                        drawArc(
                            color = arc,
                            startAngle = -90f,
                            sweepAngle = 360f * share,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$percent%", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "выполнено",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.mutedForeground
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Три карточки статистики
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(done, "Готово", c.primary, Modifier.weight(1f))
            StatCard(inWork, "В работе", c.foreground, Modifier.weight(1f))
            StatCard(overdue, "Просрочено", c.danger, Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("АКТИВНОСТЬ")
        Spacer(Modifier.height(10.dp))

        // Диаграмма по дням недели
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dayNames.forEachIndexed { i, name ->
                    val barHeight = if (counts[i] == 0) 10.dp
                    else (28 + 62 * counts[i] / maxCount).dp
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barHeight)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (i == today) c.primary else c.primarySoft)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (i == today) c.foreground else c.mutedForeground
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}