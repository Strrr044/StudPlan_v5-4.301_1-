package com.example.studplan

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.studplan.ui.theme.AppTheme

@Composable
private fun SwitchRow(
    title: String,
    hint: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.mutedForeground
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.primary,
                checkedThumbColor = c.onPrimary
            )
        )
    }
}

@Composable
private fun TimeRow(label: String, value: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.mutedForeground,
            modifier = Modifier.weight(1f)
        )
        TimeField(value = value, onValueChange = onChange, modifier = Modifier.width(120.dp))
    }
}

@Composable
private fun MinutesPicker(selected: Int, onSelect: (Int) -> Unit) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.muted)
            .padding(4.dp)
    ) {
        listOf(10, 15, 30).forEach { m ->
            val isSel = m == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSel) c.primary else Color.Transparent)
                    .clickable { onSelect(m) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "за $m мин",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSel) c.onPrimary else c.mutedForeground
                )
            }
        }
    }
}

@Composable
fun NotificationsSheet(
    settings: NotifSettings,
    onChange: (NotifSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val c = AppTheme.colors
    val context = LocalContext.current
    var denied by remember { mutableStateOf(settings.enabled && !Notifier.hasPermission(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            denied = false
            onChange(settings.copy(enabled = true))
        } else {
            denied = true
        }
    }

    fun turnOn() {
        if (Notifier.hasPermission(context)) {
            denied = false
            onChange(settings.copy(enabled = true))
        } else {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    FormSheet(
        title = "Уведомления",
        subtitle = null,
        onDismiss = onDismiss
    ) { finish ->
        SwitchRow(
            title = "Напоминания",
            hint = "Дедлайны и занятия",
            checked = settings.enabled,
            onChange = { on ->
                if (on) turnOn() else onChange(settings.copy(enabled = false))
            }
        )

        if (denied) {
            Text(
                "Нет разрешения на уведомления. Включи их для StudPlan в настройках телефона.",
                style = MaterialTheme.typography.bodySmall,
                color = c.danger,
                modifier = Modifier.padding(top = 4.dp)
            )
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }) { Text("Открыть настройки") }
        }

        if (settings.enabled) {
            Spacer(Modifier.height(16.dp))
            SectionLabel("ДЕДЛАЙНЫ")
            Spacer(Modifier.height(4.dp))
            SwitchRow(
                title = "За день до срока",
                hint = "Только для невыполненных заданий со сроком",
                checked = settings.dayBefore,
                onChange = { onChange(settings.copy(dayBefore = it)) }
            )
            if (settings.dayBefore) {
                TimeRow("Во сколько", settings.dayBeforeTime) {
                    onChange(settings.copy(dayBeforeTime = it))
                }
            }
            SwitchRow(
                title = "В день срока",
                hint = null,
                checked = settings.sameDay,
                onChange = { onChange(settings.copy(sameDay = it)) }
            )
            if (settings.sameDay) {
                TimeRow("Во сколько", settings.sameDayTime) {
                    onChange(settings.copy(sameDayTime = it))
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("ЗАНЯТИЯ")
            Spacer(Modifier.height(4.dp))
            SwitchRow(
                title = "Перед началом занятия",
                hint = null,
                checked = settings.lessons,
                onChange = { onChange(settings.copy(lessons = it)) }
            )
            if (settings.lessons) {
                MinutesPicker(settings.lessonBefore) {
                    onChange(settings.copy(lessonBefore = it))
                }
                Spacer(Modifier.height(8.dp))
            }



            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    if (Notifier.hasPermission(context)) {
                        Notifier.show(
                            context, 999, "StudPlan",
                            "Так будут выглядеть напоминания"
                        )
                    } else {
                        denied = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Проверить уведомление") }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { finish(onDismiss) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Готово") }
    }
}