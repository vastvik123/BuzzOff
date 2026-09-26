package com.buzzoff

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private val SNOOZE_PRESETS = listOf(1, 2, 5, 10, 15)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val categories by AlarmStore.categories.collectAsState()
    val alarms by AlarmStore.alarms.collectAsState()
    var pinSet by remember { mutableStateOf(Pin.isSet(ctx)) }
    var editingPin by remember { mutableStateOf(false) }
    var snooze by remember { mutableIntStateOf(Prefs.snoozeMinutes(ctx)) }
    var autoStop by remember { mutableIntStateOf(Prefs.autoStopMinutes(ctx)) }
    var ramp by remember { mutableStateOf(Prefs.rampVolume(ctx)) }
    var renaming by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf(false) }
    var customSnooze by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { GradientTopBar(title = "Settings", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(title = "PIN") {
                SettingRow(
                    title = if (pinSet) "Change PIN" else "Set PIN",
                    value = if (pinSet) "Used for \"I'm up\"" else "Not set yet",
                    onClick = { editingPin = true },
                )
            }

            SectionCard(
                title = "Categories",
                trailing = {
                    TextButton(onClick = { adding = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("Add")
                    }
                },
            ) {
                for (category in categories) {
                    val count = alarms.count { it.categoryId == category.id }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(category.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (count == 1) "1 alarm" else "$count alarms",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { renaming = category }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Rename ${category.name}")
                        }
                        IconButton(onClick = { deleting = category }, enabled = categories.size > 1) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${category.name}")
                        }
                    }
                }
            }

            SectionCard(title = "Ringing") {
                Text("Snooze length", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (minutes in SNOOZE_PRESETS) {
                        FilterChip(
                            selected = snooze == minutes,
                            onClick = { snooze = minutes; Prefs.setSnoozeMinutes(ctx, minutes) },
                            label = { Text("$minutes min") },
                            colors = purpleChipColors(),
                        )
                    }
                    val isCustom = snooze !in SNOOZE_PRESETS
                    FilterChip(
                        selected = isCustom,
                        onClick = { customSnooze = true },
                        label = { Text(if (isCustom) "Custom · $snooze min" else "Custom") },
                        leadingIcon = if (isCustom) null else {
                            { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        },
                        colors = purpleChipColors(),
                    )
                }
                Text("Stop ringing after", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (minutes in listOf(5, 10, 15, 30)) {
                        FilterChip(
                            selected = autoStop == minutes,
                            onClick = { autoStop = minutes; Prefs.setAutoStopMinutes(ctx, minutes) },
                            label = { Text("$minutes min") },
                            colors = purpleChipColors(),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Gradually increase volume", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Starts soft and reaches full volume in 30 seconds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = ramp, onCheckedChange = { ramp = it; Prefs.setRampVolume(ctx, it) })
                }
            }

            SectionCard(title = "Test") {
                Text(
                    "Make sure alarms can ring and show on your lock screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        ContextCompat.startForegroundService(
                            ctx, AlarmService.intent(ctx, AlarmService.ACTION_RING, AlarmService.TEST_ALARM_ID)
                        )
                        ctx.startActivity(Intent(ctx, RingingActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Ring a test alarm now") }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (editingPin) {
        SetPinDialog(
            requireCurrent = pinSet,
            onDismiss = { editingPin = false },
            onSaved = { editingPin = false; pinSet = true },
        )
    }
    if (customSnooze) {
        MinutesDialog(
            title = "Custom snooze length",
            initial = snooze,
            onDismiss = { customSnooze = false },
            onConfirm = {
                customSnooze = false
                snooze = it
                Prefs.setSnoozeMinutes(ctx, it)
            },
        )
    }
    if (adding) {
        NameDialog(
            title = "New category",
            onDismiss = { adding = false },
            onConfirm = { adding = false; AlarmStore.addCategory(ctx, it) },
        )
    }
    renaming?.let { category ->
        NameDialog(
            title = "Rename category",
            initial = category.name,
            onDismiss = { renaming = null },
            onConfirm = { renaming = null; AlarmStore.renameCategory(ctx, category.id, it) },
        )
    }
    deleting?.let { category ->
        val doomed = alarms.filter { it.categoryId == category.id }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${category.name}?") },
            text = {
                Text(
                    if (doomed.isEmpty()) "This category has no alarms."
                    else "Its ${doomed.size} alarm(s) will be deleted too."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    doomed.forEach { AlarmScheduler.cancel(ctx, it.id) }
                    AlarmStore.deleteCategory(ctx, category.id)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** Asks for a whole number of minutes between 1 and 60. */
@Composable
private fun MinutesDialog(title: String, initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val minutes = text.toIntOrNull()
    val valid = minutes != null && minutes in 1..60
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(2) },
                label = { Text("Minutes (1–60)") },
                suffix = { Text("min") },
                singleLine = true,
                isError = text.isNotEmpty() && !valid,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(minutes!!) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            LaunchedEffect(Unit) { focus.requestFocus() }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(minutes!!) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
