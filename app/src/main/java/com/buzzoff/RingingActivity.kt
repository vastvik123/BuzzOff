package com.buzzoff

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Full-screen alarm shown over the lock screen while [AlarmService] is ringing. */
class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { BuzzOffTheme { RingingScreen(onDone = ::finish) } }
    }

    override fun onStart() {
        super.onStart()
        AlarmService.ringingScreenVisible.value = true
    }

    override fun onStop() {
        AlarmService.ringingScreenVisible.value = false
        super.onStop()
    }
}

@Composable
private fun RingingScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val ringingId by AlarmService.ringingAlarmId.collectAsState()
    LaunchedEffect(ringingId) { if (ringingId == null) onDone() }

    val alarm = remember(ringingId) { ringingId?.let { AlarmStore.get(ctx, it) } }
    val category = remember(alarm) { alarm?.let { AlarmStore.category(ctx, it.categoryId) } }
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(1_000)
        }
    }
    val pinSet = remember { Pin.isSet(ctx) }
    var askPin by remember { mutableStateOf(false) }
    val send = { action: String -> ctx.startService(AlarmService.intent(ctx, action)) }
    val (time, amPm) = timeParts(ctx, now.hour, now.minute)

    Box(Modifier.fillMaxSize().background(BrandGradientTall)) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        time,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.4f),
                        fontWeight = FontWeight.Light,
                        color = Color.White,
                    )
                    if (amPm != null) {
                        Text(
                            " $amPm",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                    }
                }
                val title = alarm?.label?.takeIf { it.isNotBlank() }
                    ?: if (ringingId == AlarmService.TEST_ALARM_ID) "Test alarm" else "Time to wake up"
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                if (category != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        category.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }

            PulsingBell()

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pinSet && category != null) {
                    Button(
                        onClick = { askPin = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = BrandIndigo),
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "I'm up · silence all ${category.name} alarms",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val outline = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.8f))
                    OutlinedButton(
                        onClick = { send(AlarmService.ACTION_SNOOZE) },
                        border = outline,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text("Snooze ${Prefs.snoozeMinutes(ctx)} min", color = Color.White) }
                    OutlinedButton(
                        onClick = { send(AlarmService.ACTION_DISMISS) },
                        border = outline,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text("Dismiss", color = Color.White) }
                }
            }
        }
    }

    if (askPin && category != null) {
        EnterPinDialog(
            title = "I'm up",
            message = "Enter your PIN to turn off the rest of today's ${category.name} alarms.",
            onDismiss = { askPin = false },
            onCorrect = {
                askPin = false
                AlarmScheduler.silenceCategory(ctx, category.id)
                send(AlarmService.ACTION_DISMISS)
            },
        )
    }
}

@Composable
private fun PulsingBell() {
    val transition = rememberInfiniteTransition(label = "bell")
    val ripple by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = LinearEasing)),
        label = "ripple",
    )
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val wobble by transition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(160, easing = LinearEasing), RepeatMode.Reverse),
        label = "wobble",
    )
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(240.dp)
                .graphicsLayer {
                    scaleX = 0.5f + ripple * 0.5f
                    scaleY = 0.5f + ripple * 0.5f
                    alpha = 1f - ripple
                }
                .border(2.dp, Color.White, CircleShape)
        )
        Box(
            Modifier
                .size(136.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_bell),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(64.dp).graphicsLayer { rotationZ = wobble },
            )
        }
    }
}
