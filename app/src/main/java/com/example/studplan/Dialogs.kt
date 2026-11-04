package com.example.studplan

import androidx.compose.foundation.background
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.studplan.ui.theme.Manrope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studplan.ui.theme.AppTheme
import kotlinx.coroutines.launch
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

typealias Finish = (() -> Unit) -> Unit

// ---------- Данные для форм ----------

val everyDayLabels = listOf(
    "Каждый понедельник", "Каждый вторник", "Каждую среду", "Каждый четверг",
    "Каждую пятницу", "Каждую субботу", "Каждое воскресенье"
)

private val weekdaysGenitive = listOf(
    "понедельника", "вторника", "среды", "четверга",
    "пятницы", "субботы", "воскресенья"
)

// "до пятницы, 23 окт"
fun deadlineText(millis: Long): String {
    val dayIndex = (((millis / 86_400_000L) + 3) % 7).toInt()
    return "до ${weekdaysGenitive[dayIndex]}, ${shortDate(millis)}"
}

// Все предметы из занятий и заданий; недавно добавленные выше
fun collectSubjects(lessons: List<Lesson>, tasks: List<Task>): List<String> {
    val fromTasks = tasks.sortedByDescending { it.id }.map { it.subject }
    val fromLessons = lessons.sortedByDescending { it.id }.map { it.subject }
    return (fromTasks + fromLessons).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

// Если ввели "физика", а в списке уже есть "Физика", берём вариант из списка
fun canonicalSubject(input: String, subjects: List<String>): String =
    subjects.firstOrNull { it.equals(input, ignoreCase = true) } ?: input

// ---------- Мелкие элементы ----------

@Composable
fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = AppTheme.colors.mutedForeground,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
fun FieldIcon(resId: Int) {
    Icon(
        painter = painterResource(resId),
        contentDescription = null,
        tint = AppTheme.colors.mutedForeground,
        modifier = Modifier.size(20.dp)
    )
}

@Composable
fun FieldBox(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.colors.muted)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// Текстовое поле в стиле дизайна. Счётчик символов появляется только у лимита.
@Composable
fun FilledInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    maxLength: Int = Int.MAX_VALUE,
    maxLines: Int = 1,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    focusRequester: FocusRequester? = null
) {
    val c = AppTheme.colors
    Column(modifier = modifier) {
        FieldBox {
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= maxLength) onValueChange(it) },
                singleLine = maxLines == 1,
                maxLines = maxLines,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.foreground),
                cursorBrush = SolidColor(c.primary),
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (focusRequester != null) Modifier.focusRequester(focusRequester)
                        else Modifier
                    ),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.mutedForeground.copy(alpha = 0.8f)
                            )
                        }
                        inner()
                    }
                }
            )
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        if (maxLength != Int.MAX_VALUE) {
            val warnFrom = maxLength - minOf(10, maxLength / 3)
            if (value.length >= warnFrom) {
                Text(
                    "${value.length}/$maxLength",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (value.length >= maxLength) c.danger else c.mutedForeground,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

// Предмет: выпадающий список, "+ Новый предмет" закреплён сверху
@Composable
fun SubjectField(
    value: String,
    onValueChange: (String) -> Unit,
    subjects: List<String>,
    maxLength: Int,
    optional: Boolean
) {
    val c = AppTheme.colors
    var typing by remember {
        mutableStateOf(subjects.isEmpty() || (value.isNotBlank() && value !in subjects))
    }
    var expanded by remember { mutableStateOf(false) }
    var focusTick by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(focusTick) {
        if (focusTick > 0) focus.requestFocus()
    }

    if (typing) {
        val backToList: (@Composable RowScope.() -> Unit)? =
            if (subjects.isEmpty()) null else {
                {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Выбрать из списка",
                        tint = c.mutedForeground,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                typing = false
                                if (value !in subjects) onValueChange("")
                            }
                    )
                }
            }
        FilledInput(
            value = value,
            onValueChange = onValueChange,
            placeholder = if (optional) "Название предмета (необязательно)" else "Название предмета",
            maxLength = maxLength,
            trailing = backToList,
            focusRequester = focus
        )
    } else {
        Box {
            FieldBox(onClick = { expanded = true }) {
                Text(
                    text = when {
                        value.isNotBlank() -> value
                        optional -> "Без предмета"
                        else -> "Выбери предмет"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isNotBlank()) c.foreground else c.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = c.mutedForeground,
                    modifier = Modifier.size(24.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 260.dp)
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "+ Новый предмет",
                            style = MaterialTheme.typography.titleSmall,
                            color = c.primary
                        )
                    },
                    onClick = {
                        expanded = false
                        typing = true
                        onValueChange("")
                        focusTick++
                    }
                )
                if (optional) {
                    DropdownMenuItem(
                        text = { Text("Без предмета") },
                        onClick = {
                            onValueChange("")
                            expanded = false
                        }
                    )
                }
                HorizontalDivider(color = c.border)
                Column(
                    modifier = Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    subjects.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s) },
                            onClick = {
                                onValueChange(s)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// Выбор времени по иконке часов
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    var show by remember { mutableStateOf(false) }

    FieldBox(modifier = modifier, onClick = { show = true }) {
        Text(
            if (value.isEmpty()) "--:--" else value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (value.isEmpty()) c.mutedForeground else c.foreground,
            modifier = Modifier.weight(1f)
        )
        FieldIcon(R.drawable.ic_clock)
    }

    if (show) {
        val parts = value.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val state = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(String.format(Locale.US, "%02d:%02d", state.hour, state.minute))
                    show = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) { Text("Отмена") }
            },
            text = {
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme,
                    shapes = MaterialTheme.shapes,
                    typography = MaterialTheme.typography.copy(
                        displayLarge = TextStyle(
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Bold,
                            fontSize = 48.sp,
                            lineHeight = 56.sp
                        )
                    )
                ) {
                    TimePicker(state = state)
                }
            }
        )
    }
}

// Поле "Конец": только для чтения
@Composable
fun ReadOnlyTime(value: String, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    FieldBox(modifier = modifier.alpha(0.7f)) {
        Text(
            if (value.isEmpty()) "--:--" else value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (value.isEmpty()) c.mutedForeground else c.foreground,
            modifier = Modifier.weight(1f)
        )
        FieldIcon(R.drawable.ic_clock)
    }
}

@Composable
fun SheetButtons(
    confirmText: String,
    enabled: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val c = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onCancel)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Отмена", style = MaterialTheme.typography.titleMedium, color = c.primary)
        }
        Spacer(Modifier.width(12.dp))
        val shape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .weight(1.4f)
                .alpha(if (enabled) 1f else 0.4f)
                .then(
                    if (enabled) Modifier.shadow(
                        8.dp, shape,
                        ambientColor = c.primary.copy(alpha = 0.35f),
                        spotColor = c.primary.copy(alpha = 0.35f)
                    ) else Modifier
                )
                .clip(shape)
                .background(c.primary)
                .then(if (enabled) Modifier.clickable(onClick = onConfirm) else Modifier)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(confirmText, style = MaterialTheme.typography.titleMedium, color = c.onPrimary)
        }
    }
}

// Нижняя шторка. finish { ... } плавно закрывает шторку и потом выполняет действие.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(
    title: String,
    subtitle: (@Composable () -> Unit)?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(Finish) -> Unit
) {
    val c = AppTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val finish: Finish = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.border)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 24.sp,
                    lineHeight = 30.sp
                )
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                subtitle()
            }
            Spacer(Modifier.height(20.dp))
            content(finish)
        }
    }
}

// ---------- Шторка занятия ----------

private fun fullDate(millis: Long): String {
    val f = SimpleDateFormat("d MMMM", Locale("ru"))
    f.timeZone = TimeZone.getTimeZone("UTC")
    return f.format(Date(millis))
}

@Composable
fun LessonSheet(
    initial: Lesson?,
    dayIndex: Int,
    dayDate: Long,
    mode: UserMode,
    subjects: List<String>,
    onSave: (time: String, subject: String, room: String, date: Long, end: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val c = AppTheme.colors
    val minutes = mode.lessonMinutes
    var time by remember { mutableStateOf(initial?.time ?: "") }
    var subject by remember { mutableStateOf(initial?.subject ?: "") }
    var room by remember { mutableStateOf(initial?.room ?: "") }
    var end by remember {
        mutableStateOf(
            initial?.let { lessonEnd(it, minutes) } ?: ""
        )
    }
    var once by remember { mutableStateOf((initial?.date ?: 0L) != 0L) }
    var repeatMenu by remember { mutableStateOf(false) }
    val ready = time.isNotEmpty() && subject.isNotBlank() && end.isNotEmpty()
    val roomLabel = "Аудитория"
    val everyText = everyDayLabels[dayIndex]
    val onceText = "Только ${fullDate(dayDate)}"

    FormSheet(
        title = if (initial == null) "Новое занятие" else "Изменить занятие",
        subtitle = {
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { repeatMenu = true }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (once) onceText else everyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.primary
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = repeatMenu,
                    onDismissRequest = { repeatMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(everyText) },
                        onClick = {
                            once = false
                            repeatMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(onceText) },
                        onClick = {
                            once = true
                            repeatMenu = false
                        }
                    )
                }
            }
        },
        onDismiss = onDismiss
    ) { finish ->
        FieldLabel("Предмет")
        SubjectField(
            value = subject,
            onValueChange = { subject = it },
            subjects = subjects,
            maxLength = MAX_LESSON_SUBJECT,
            optional = false
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel("Начало")
                TimeField(
                    value = time,
                    onValueChange = {
                        time = it
                        end = endTime(it, minutes)   // конец пересчитывается сам
                    }
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel("Конец")
                TimeField(
                    value = end,
                    onValueChange = { end = it }     // можно поправить вручную
                )
            }
        }
        Text(
            "Конец считается сам ($minutes мин), нажми на него, чтобы изменить",
            style = MaterialTheme.typography.bodySmall,
            color = c.mutedForeground,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(16.dp))
        FieldLabel(roomLabel)
        FilledInput(
            value = room,
            onValueChange = { room = it },
            placeholder = "Например, 401",
            maxLength = MAX_LESSON_ROOM,
            trailing = { FieldIcon(R.drawable.ic_map_pin) }
        )

        Spacer(Modifier.height(24.dp))
        SheetButtons(
            confirmText = if (initial == null) "Добавить" else "Сохранить",
            enabled = ready,
            onCancel = { finish(onDismiss) },
            onConfirm = {
                val s = canonicalSubject(subject.trim(), subjects)
                // Своё время сохраняем только если оно отличается от автоматического
                val savedEnd = if (end == endTime(time, minutes)) "" else end
                finish {
                    onSave(time, s, room.trim(), if (once) dayDate else 0L, savedEnd)
                }
            }
        )

        if (onDelete != null) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { finish(onDelete) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Удалить занятие",
                    style = MaterialTheme.typography.titleSmall,
                    color = AppTheme.colors.danger
                )
            }
        }
    }
}

@Composable
private fun PriorityPicker(selected: Int, onSelect: (Int) -> Unit) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.muted)
            .padding(4.dp)
    ) {
        priorityNames.forEachIndexed { index, name ->
            val isSel = index == selected
            val (fg, bg) = when (index) {
                2 -> c.danger to c.dangerSoft
                1 -> c.warning to c.warningSoft
                else -> c.success to c.successSoft
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSel) bg else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSel) fg else c.mutedForeground
                )
            }
        }
    }
}

@Composable
private fun DeadlineChip(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val c = AppTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.primary else c.muted)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = if (selected) c.onPrimary else c.foreground,
            maxLines = 1,
            softWrap = false
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSheet(
    initial: Task?,
    subjects: List<String>,
    onSave: (title: String, subject: String, priority: Int, deadline: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val c = AppTheme.colors
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var subject by remember { mutableStateOf(initial?.subject ?: "") }
    var priority by remember { mutableIntStateOf(initial?.priority ?: 1) }
    var deadline by remember { mutableLongStateOf(initial?.deadline ?: 0L) }
    var showPicker by remember { mutableStateOf(false) }

    FormSheet(
        title = if (initial == null) "Новое задание" else "Изменить задание",
        subtitle = null,
        onDismiss = onDismiss
    ) { finish ->
        FieldLabel("Что сделать")
        FilledInput(
            value = title,
            onValueChange = { title = it },
            placeholder = "Например, отчёт по лабораторной",
            maxLength = MAX_TASK_TITLE,
            maxLines = 2
        )

        Spacer(Modifier.height(16.dp))
        FieldLabel("Предмет")
        SubjectField(
            value = subject,
            onValueChange = { subject = it },
            subjects = subjects,
            maxLength = MAX_TASK_SUBJECT,
            optional = true
        )

        Spacer(Modifier.height(16.dp))
        FieldLabel("Приоритет")
        PriorityPicker(selected = priority, onSelect = { priority = it })

        Spacer(Modifier.height(16.dp))
        FieldLabel("Срок сдачи")
        val today = todayUtc()
        val presets = listOf(
            "Сегодня" to today,
            "Завтра" to today + 86_400_000L,
            "Через неделю" to today + 7 * 86_400_000L
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            presets.forEach { (label, value) ->
                val isSel = deadline == value
                DeadlineChip(
                    label = label,
                    selected = isSel,
                    modifier = Modifier.weight(if (label == "Через неделю") 1.5f else 1f),
                    onClick = { deadline = if (isSel) 0L else value }
                )
            }
            val custom = deadline != 0L && presets.none { it.second == deadline }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (custom) c.primary else c.muted)
                    .clickable { showPicker = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_calendar_days),
                    contentDescription = "Выбрать дату",
                    tint = if (custom) c.onPrimary else c.mutedForeground,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (deadline != 0L) {
            Text(
                deadlineText(deadline),
                style = MaterialTheme.typography.labelMedium,
                color = c.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(24.dp))
        SheetButtons(
            confirmText = if (initial == null) "Добавить" else "Сохранить",
            enabled = title.isNotBlank(),
            onCancel = { finish(onDismiss) },
            onConfirm = {
                val s = canonicalSubject(subject.trim(), subjects)
                finish { onSave(title.trim(), s, priority, deadline) }
            }
        )
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = if (deadline == 0L) null else deadline
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    deadline = state.selectedDateMillis ?: 0L
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}