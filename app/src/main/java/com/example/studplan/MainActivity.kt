package com.example.studplan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studplan.ui.theme.AppTheme
import com.example.studplan.ui.theme.StudPlanTheme

enum class AppTab(val title: String, val iconRes: Int) {
    Home("Главная", R.drawable.ic_home),
    Timetable("Расписание", R.drawable.ic_calendar_days),
    Tasks("Задания", R.drawable.ic_circle_check),
    Progress("Прогресс", R.drawable.ic_trending_up)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = Storage(this)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var darkTheme by rememberSaveable { mutableStateOf(storage.loadDark(systemDark)) }

            // Значки статус-бара и панели навигации подстраиваются под тему приложения
            DisposableEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    )
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            StudPlanTheme(darkTheme = darkTheme) {
                StudPlanApp(
                    storage = storage,
                    darkTheme = darkTheme,
                    onThemeChange = {
                        darkTheme = it
                        storage.saveDark(it)
                    }
                )
            }
        }
    }
}

@Composable
fun StudPlanApp(
    storage: Storage,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val tasks = remember { storage.loadTasks().toMutableStateList() }
    val lessons = remember { storage.loadLessons().toMutableStateList() }
    val currentMode = UserMode.Student
    var notif by remember { mutableStateOf(storage.loadNotif()) }

    // Панель сворачивается при прокрутке вниз и возвращается при прокрутке вверх
    var barCollapsed by remember { mutableStateOf(false) }
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -6f) barCollapsed = true
                else if (available.y > 6f) barCollapsed = false
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(Unit) {
        Notifier.createChannel(context)
    }
    LaunchedEffect(Unit) {
        snapshotFlow { tasks.toList() }.collect { storage.saveTasks(it) }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { lessons.toList() }.collect { storage.saveLessons(it) }
    }
    // Любое изменение заданий, занятий или настроек пересчитывает напоминания
    LaunchedEffect(notif) {
        snapshotFlow { tasks.toList() to lessons.toList() }.collect { (t, l) ->
            Notifier.reschedule(context, t, l, notif)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppTheme.colors.background,
        bottomBar = {
            BottomBar(
                selected = selectedTab,
                collapsed = barCollapsed,
                onSelect = {
                    selectedTab = it
                    barCollapsed = false
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollConnection)
        ) {
            when (AppTab.entries[selectedTab]) {
                AppTab.Home -> HomeScreen(
                    tasks = tasks,
                    lessons = lessons,
                    mode = currentMode,
                    darkTheme = darkTheme,
                    onThemeChange = onThemeChange,
                    notif = notif,
                    onNotifChange = {
                        notif = it
                        storage.saveNotif(it)
                    }
                )
                AppTab.Timetable -> TimetableScreen(
                    lessons = lessons,
                    subjects = collectSubjects(lessons, tasks),
                    mode = currentMode,
                    lastAsuSync = storage.loadAsuSyncTime(),
                    onAsuSync = { time -> storage.saveAsuSyncTime(time) }
                )
                AppTab.Tasks -> TasksScreen(tasks, collectSubjects(lessons, tasks))
                AppTab.Progress -> ProgressScreen(tasks)
            }
        }
    }
}

@Composable
fun BottomBar(selected: Int, collapsed: Boolean, onSelect: (Int) -> Unit) {
    val c = AppTheme.colors
    Column(modifier = Modifier.animateContentSize()) {
        HorizontalDivider(color = c.border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.card)
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = if (collapsed) 4.dp else 8.dp)
        ) {
            AppTab.entries.forEachIndexed { index, tab ->
                val isSelected = index == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) c.primary else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = tab.title,
                            tint = if (isSelected) c.onPrimary else c.mutedForeground,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    AnimatedVisibility(visible = !collapsed) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                tab.title,
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) c.foreground else c.mutedForeground
                            )
                        }
                    }
                }
            }
        }
    }
}