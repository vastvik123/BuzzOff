package com.buzzoff

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import java.time.ZonedDateTime

private val GAP_OPTIONS = listOf(2, 5, 10, 15)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    alarmId: Int?,
    initialCategoryId: Int?,
    onDone: (message: String?, undo: (() -> Unit)?) -> Unit,
) {
    val ctx = LocalContext.current
    val existing = remember { alarmId?.let { AlarmStore.get(ctx, it) } }
    val categories by AlarmStore.categories.collectAsState()

    val timeState = rememberTimePickerState(
        initialHour = existing?.hour ?: 7,
        initialMinute = existing?.minute ?: 0,
        is24Hour = DateFormat.is24HourFormat(ctx),
    )
    var typing by rememberSaveable { mutableStateOf(false) }
    var days by remember { mutableStateOf(existing?.days ?: emptySet()) }
    var custom by remember { mutableStateOf(existing != null && Frequency.of(existing.days) == Frequency.CUSTOM) }
    var categoryId by remember {
        mutableIntStateOf(existing?.categoryId ?: initialCategoryId ?: categories.first().id)
    }
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var ringtone by remember { mutableStateOf(existing?.ringtone) }
    var vibrate by remember { mutableStateOf(existing?.vibrate ?: true) }
    var extra by remember { mutableIntStateOf(0) }
    var gap by remember { mutableIntStateOf(5) }
    var newCategory by remember { mutableStateOf(false) }

    val draft = Alarm(
        id = existing?.id ?: AlarmStore.newId(ctx),
        hour = timeState.hour,
        minute = timeState.minute,
        days = days,
        categoryId = categoryId,
        label = label.trim(),
        ringtone = ringtone,
        vibrate = vibrate,
    )
    val ringsIn = formatCountdown(
        draft.nextRing(ZonedDateTime.now()).toInstant().toEpochMilli() - System.currentTimeMillis()
    )

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            val default = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = uri?.takeIf { it != default }?.toString()
        }
    }
    val ringtoneName = remember(ringtone) { ringtoneTitle(ctx, ringtone) }

    fun save() {
        val first = draft.copy(enabled = true, skipUntil = 0L)
        val added = (1..extra).map { i ->
            val t = (first.hour * 60 + first.minute + i * gap) % (24 * 60)
            first.copy(id = first.id + i, hour = t / 60, minute = t % 60)
        }
        AlarmStore.save(ctx, AlarmStore.all(ctx).filter { it.id != first.id } + first + added)
        AlarmScheduler.rescheduleAll(ctx)
        onDone(if (added.isEmpty()) "Alarm set · rings in $ringsIn" else "${added.size + 1} alarms set · first rings in $ringsIn", null)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GradientTopBar(
                title = if (existing == null) "New alarm" else "Edit alarm",
                onBack = { onDone(null, null) },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = {
                            AlarmStore.delete(ctx, existing.id)
                            AlarmScheduler.cancel(ctx, existing.id)
                            onDone("Alarm deleted") {
                                AlarmStore.upsert(ctx, existing)
                                AlarmScheduler.rescheduleAll(ctx)
                            }
                        }) { Icon(Icons.Filled.Delete, contentDescription = "Delete alarm") }
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                GradientButton(
                    text = if (existing == null) "Save alarm" else "Save changes",
                    onClick = ::save,
                    leading = { Icon(Icons.Filled.Check, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Time: pick on the clock or type it.
            SectionCard(title = "Time") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !typing,
                        onClick = { typing = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = { Icon(painterResource(R.drawable.ic_clock), null, Modifier.size(18.dp)) },
                    ) { Text("Clock") }
                    SegmentedButton(
                        selected = typing,
                        onClick = { typing = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = { Icon(painterResource(R.drawable.ic_keyboard), null, Modifier.size(18.dp)) },
                    ) { Text("Keyboard") }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (typing) TimeInput(state = timeState) else TimePicker(state = timeState)
                }
                Text(
                    "Rings in $ringsIn",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = "Repeat") {
                val selected = if (custom) Frequency.CUSTOM else Frequency.of(days)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (frequency in Frequency.entries) {
                        FilterChip(
                            selected = selected == frequency,
                            onClick = {
                                custom = frequency == Frequency.CUSTOM
                                if (frequency.days != null) days = frequency.days
                            },
                            label = { Text(frequency.label) },
                            colors = purpleChipColors(),
                        )
                    }
                }
                if (selected == Frequency.CUSTOM) {
                    DayToggles(days, onChange = { days = it })
                }
            }

            SectionCard(title = "Category") {
                Text(
                    "\"I'm up\" silences the rest of today's alarms in the same category.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (category in categories) {
                        FilterChip(
                            selected = category.id == categoryId,
                            onClick = { categoryId = category.id },
                            label = { Text(category.name) },
                            colors = purpleChipColors(),
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { newCategory = true },
                        label = { Text("New") },
                        leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) },
                    )
                }
            }

            SectionCard(title = "Details") {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    label = { Text("Label (optional)") },
                    placeholder = { Text("e.g. Gym day") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingRow(
                    title = "Sound",
                    value = ringtoneName,
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                ringtone?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                            )
                        try {
                            ringtonePicker.launch(intent)
                        } catch (_: ActivityNotFoundException) {
                        }
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vibrate", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                }
            }

            if (existing == null) {
                SectionCard(title = "Backup alarms") {
                    Text(
                        "Add more alarms after this one, in case you sleep through it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Extra alarms", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        FilledTonalIconButton(onClick = { if (extra > 0) extra-- }, enabled = extra > 0) {
                            Text("−", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            "$extra",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(40.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        FilledTonalIconButton(onClick = { if (extra < 10) extra++ }, enabled = extra < 10) {
                            Icon(Icons.Filled.Add, contentDescription = "More")
                        }
                    }
                    if (extra > 0) {
                        Text("Every", style = MaterialTheme.typography.bodyLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (minutes in GAP_OPTIONS) {
                                FilterChip(
                                    selected = gap == minutes,
                                    onClick = { gap = minutes },
                                    label = { Text("$minutes min") },
                                    colors = purpleChipColors(),
                                )
                            }
                        }
                        val times = (0..extra).joinToString(", ") {
                            val t = (draft.hour * 60 + draft.minute + it * gap) % (24 * 60)
                            formatTime(ctx, t / 60, t % 60)
                        }
                        Text(
                            "Rings at $times",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (newCategory) {
        NameDialog(
            title = "New category",
            onDismiss = { newCategory = false },
            onConfirm = { name ->
                newCategory = false
                categoryId = AlarmStore.addCategory(ctx, name).id
            },
        )
    }
}

@Composable
fun purpleChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun ringtoneTitle(ctx: Context, uri: String?): String {
    if (uri == null) return "Default alarm sound"
    return runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(uri))?.getTitle(ctx) }.getOrNull()
        ?: "Custom sound"
}

