package com.buzzoff

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class MainActivity : ComponentActivity() {
    /** Bumped on every resume so permission warnings are re-checked. */
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        AlarmStore.load(this)
        setContent { BuzzOffTheme { App(resumeTick.intValue) } }
    }

    override fun onResume() {
        super.onResume()
        AlarmScheduler.rescheduleAll(this)
        resumeTick.intValue++
    }
}

/** Shows [message] with an optional Undo action. */
fun SnackbarHostState.notify(scope: CoroutineScope, message: String, undo: (() -> Unit)? = null) {
    scope.launch {
        currentSnackbarData?.dismiss()
        val result = showSnackbar(
            message,
            actionLabel = if (undo != null) "Undo" else null,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) undo?.invoke()
    }
}

/** Screens are encoded as strings so they survive rotation: "home", "settings", "edit:<alarmId>:<categoryId>". */
@Composable
private fun App(resumeTick: Int) {
    var screen by rememberSaveable { mutableStateOf("home") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = screen != "home") { screen = "home" }

    when {
        screen == "settings" -> SettingsScreen(onBack = { screen = "home" })
        screen.startsWith("edit:") -> {
            val (_, alarmId, categoryId) = screen.split(":")
            EditorScreen(
                alarmId = alarmId.toIntOrNull(),
                initialCategoryId = categoryId.toIntOrNull(),
                onDone = { message, undo ->
                    screen = "home"
                    if (message != null) snackbar.notify(scope, message, undo)
                },
            )
        }
        else -> HomeScreen(
            resumeTick = resumeTick,
            snackbar = snackbar,
            onNew = { categoryId -> screen = "edit:new:$categoryId" },
            onEdit = { alarmId -> screen = "edit:$alarmId:" },
            onSettings = { screen = "settings" },
        )
    }
}

@Composable
private fun HomeScreen(
    resumeTick: Int,
    snackbar: SnackbarHostState,
    onNew: (categoryId: Int) -> Unit,
    onEdit: (alarmId: Int) -> Unit,
    onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val alarms by AlarmStore.alarms.collectAsState()
    val categories by AlarmStore.categories.collectAsState()
    val snooze by AlarmStore.snooze.collectAsState()
    val scope = rememberCoroutineScope()
    var pinSet by remember(resumeTick) { mutableStateOf(Pin.isSet(ctx)) }
    var silencing by remember { mutableStateOf<Category?>(null) }
    var settingPinFor by remember { mutableStateOf<Category?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(alarms, snooze, resumeTick) {
        while (true) {
            now = System.currentTimeMillis()
            delay(20_000)
        }
    }

    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val issues = remember(resumeTick) { setupIssues(ctx) }

    fun silence(category: Category) {
        AlarmScheduler.silenceCategory(ctx, category.id)
        val ringing = AlarmService.ringingAlarmId.value
        if (ringing != null && AlarmStore.get(ctx, ringing)?.categoryId == category.id) {
            ctx.startService(AlarmService.intent(ctx, AlarmService.ACTION_DISMISS))
        }
        snackbar.notify(scope, "${category.name} alarms are off for today") {
            AlarmScheduler.unsilenceCategory(ctx, category.id)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            GradientButton(
                text = "New alarm",
                onClick = { onNew(categories.first().id) },
                leading = { Icon(Icons.Filled.Add, contentDescription = null) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize()) {
            HomeHeader(
                alarms = alarms,
                snooze = snooze,
                now = now,
                onSettings = onSettings,
                onCancelSnooze = { AlarmScheduler.cancelSnooze(ctx) },
            )
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(issues, key = { "issue-" + it.title }) { SetupCard(it) }
                if (!pinSet) {
                    item(key = "pin") {
                        SetupCard(
                            Issue(
                                "Set your PIN",
                                "You'll need it to silence a category with \"I'm up\".",
                                null,
                            ),
                            actionLabel = "Set PIN",
                            onAction = { settingPinFor = Category(-1, "") },
                        )
                    }
                }
                for (category in categories) {
                    val inCategory = alarms.filter { it.categoryId == category.id }
                    item(key = "cat-${category.id}") {
                        CategoryHeader(
                            category = category,
                            alarms = inCategory,
                            now = now,
                            onUp = { if (pinSet) silencing = category else settingPinFor = category },
                            onUndo = {
                                AlarmScheduler.unsilenceCategory(ctx, category.id)
                                snackbar.notify(scope, "${category.name} alarms are back on")
                            },
                            onAdd = { onNew(category.id) },
                        )
                    }
                    if (inCategory.isEmpty()) {
                        item(key = "empty-${category.id}") {
                            EmptyCategory(onAdd = { onNew(category.id) })
                        }
                    }
                    items(inCategory, key = { "alarm-${it.id}" }) { alarm ->
                        SwipeableAlarmRow(
                            alarm = alarm,
                            now = now,
                            onClick = { onEdit(alarm.id) },
                            onToggle = { on ->
                                AlarmStore.upsert(ctx, alarm.copy(enabled = on, skipUntil = 0L))
                                AlarmScheduler.rescheduleAll(ctx)
                                if (on) {
                                    val next = AlarmStore.get(ctx, alarm.id)!!.nextRing(ZonedDateTime.now())
                                    snackbar.notify(
                                        scope,
                                        "Rings in " + formatCountdown(next.toInstant().toEpochMilli() - System.currentTimeMillis()),
                                    )
                                }
                            },
                            onDelete = {
                                AlarmStore.delete(ctx, alarm.id)
                                AlarmScheduler.cancel(ctx, alarm.id)
                                snackbar.notify(scope, "Alarm deleted") {
                                    AlarmStore.upsert(ctx, alarm)
                                    AlarmScheduler.rescheduleAll(ctx)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    silencing?.let { category ->
        EnterPinDialog(
            title = "I'm up",
            message = "Enter your PIN to turn off the rest of today's ${category.name} alarms.",
            onDismiss = { silencing = null },
            onCorrect = {
                silencing = null
                silence(category)
            },
        )
    }
    settingPinFor?.let { category ->
        SetPinDialog(
            requireCurrent = false,
            onDismiss = { settingPinFor = null },
            onSaved = {
                settingPinFor = null
                pinSet = true
                // Came from an "I'm up" tap: the user just proved intent, so silence right away.
                if (category.id >= 0) silence(category) else snackbar.notify(scope, "PIN saved")
            },
        )
    }
}

@Composable
private fun HomeHeader(
    alarms: List<Alarm>,
    snooze: Pair<Long, Int>,
    now: Long,
    onSettings: () -> Unit,
    onCancelSnooze: () -> Unit,
) {
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    val nowZoned = Instant.ofEpochMilli(now).atZone(zone)
    val nextAlarm = alarms.filter { it.enabled }.minOfOrNull { it.nextRing(nowZoned).toInstant() }
    val snoozeAt = snooze.first.takeIf { it > now }
    val next = listOfNotNull(nextAlarm, snoozeAt?.let(Instant::ofEpochMilli)).minOrNull()

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(BrandGradientTall)
            .statusBarsPadding()
            .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 28.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "BuzzOff",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (next == null) {
                Text("No alarms set", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(
                    "Tap New alarm to add your first one",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                )
            } else {
                Text("NEXT ALARM", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                Text(
                    formatDayTime(ctx, next.atZone(zone)),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    "Rings in " + formatCountdown(next.toEpochMilli() - now),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            if (snoozeAt != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val t = Instant.ofEpochMilli(snoozeAt).atZone(zone)
                    Text(
                        "Snoozed until ${formatTime(ctx, t.hour, t.minute)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(onClick = onCancelSnooze) { Text("Cancel", color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    category: Category,
    alarms: List<Alarm>,
    now: Long,
    onUp: () -> Unit,
    onUndo: () -> Unit,
    onAdd: () -> Unit,
) {
    val silenced = alarms.any { it.isSilenced(now) }
    val active = alarms.count { it.enabled && !it.isSilenced(now) }
    val subtitle = when {
        silenced -> "Off for the rest of today"
        alarms.isEmpty() -> "No alarms"
        else -> "$active of ${alarms.size} on"
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(category.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            silenced -> TextButton(onClick = onUndo) { Text("Turn back on") }
            active > 0 -> GradientButton(
                text = "I'm up",
                onClick = onUp,
                height = 40.dp,
                leading = { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = "Add alarm to ${category.name}", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EmptyCategory(onAdd: () -> Unit) {
    Card(
        onClick = onAdd,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "No alarms here yet. Tap to add one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableAlarmRow(
    alarm: Alarm,
    now: Long,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete()
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        },
    ) {
        AlarmRow(alarm, now, onClick, onToggle)
    }
}

@Composable
private fun AlarmRow(alarm: Alarm, now: Long, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val silenced = alarm.isSilenced(now)
    val active = alarm.enabled && !silenced
    val (time, amPm) = timeParts(ctx, alarm.hour, alarm.minute)
    val details = listOfNotNull(
        alarm.label.takeIf { it.isNotBlank() },
        repeatLabel(alarm.days),
        if (silenced) "off for today" else null,
    ).joinToString(" · ")

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).alpha(if (active) 1f else 0.5f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(time, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
                    if (amPm != null) {
                        Text(
                            " $amPm",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
                Text(
                    details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

class Issue(val title: String, val text: String, val intent: Intent?)

@SuppressLint("BatteryLife")
private fun setupIssues(ctx: Context): List<Issue> {
    val pkg = "package:${ctx.packageName}".toUri()
    val issues = mutableListOf<Issue>()
    if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
        issues += Issue(
            "Allow notifications",
            "The alarm screen appears through a notification.",
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !AlarmScheduler.canScheduleExact(ctx)) {
        issues += Issue(
            "Allow exact alarms",
            "Without it, alarms can't ring on time.",
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg),
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        !ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    ) {
        issues += Issue(
            "Allow full-screen alarms",
            "Needed to show the alarm over the lock screen.",
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg),
        )
    }
    if (!ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)) {
        issues += Issue(
            "Turn off battery optimization",
            "OnePlus phones stop background apps, which can silence alarms.",
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
        )
    }
    return issues
}

@Composable
private fun SetupCard(issue: Issue, actionLabel: String = "Fix", onAction: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val action = onAction ?: {
        try {
            ctx.startActivity(issue.intent)
        } catch (e: ActivityNotFoundException) {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${ctx.packageName}".toUri())
            )
        }
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().clickable(onClick = action),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (onAction == null) Icons.Filled.Warning else Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(issue.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(issue.text, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = action) { Text(actionLabel) }
        }
    }
}
