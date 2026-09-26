package com.buzzoff

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun formatTime(ctx: Context, hour: Int, minute: Int): String {
    val pattern = if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a"
    return LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(pattern))
}

/** Time split into the big digits and an optional AM/PM suffix. */
fun timeParts(ctx: Context, hour: Int, minute: Int): Pair<String, String?> {
    val time = LocalTime.of(hour, minute)
    return if (DateFormat.is24HourFormat(ctx)) {
        time.format(DateTimeFormatter.ofPattern("HH:mm")) to null
    } else {
        time.format(DateTimeFormatter.ofPattern("h:mm")) to time.format(DateTimeFormatter.ofPattern("a"))
    }
}

/** "Today, 6:00 AM", "Tomorrow, 6:00 AM" or "Wed, 6:00 AM". */
fun formatDayTime(ctx: Context, time: ZonedDateTime): String {
    val today = ZonedDateTime.now(time.zone).toLocalDate()
    val day = when (time.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> time.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
    return "$day, ${formatTime(ctx, time.hour, time.minute)}"
}

/** "7 h 12 min", "45 min", "2 d 3 h". */
fun formatCountdown(millis: Long): String {
    val totalMinutes = ((millis + 59_999) / 60_000).coerceAtLeast(0)
    if (totalMinutes < 1) return "less than a minute"
    val days = totalMinutes / 1440
    val hours = (totalMinutes % 1440) / 60
    val minutes = totalMinutes % 60
    return buildList {
        if (days > 0) add("$days d")
        if (hours > 0) add("$hours h")
        if (minutes > 0 && days == 0L) add("$minutes min")
    }.joinToString(" ")
}

/** Seven round toggles, Monday to Sunday. */
@Composable
fun DayToggles(days: Set<Int>, onChange: (Set<Int>) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (day in DayOfWeek.values()) {
            val on = day.value in days
            val name = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .then(
                        if (on) Modifier.background(BrandGradient)
                        else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    .clickable { onChange(if (on) days - day.value else days + day.value) }
                    .semantics {
                        contentDescription = name
                        selected = on
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) androidx.compose.ui.graphics.Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A rounded card with a small title, used to group form sections. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                trailing()
            }
            content()
        }
    }
}

@Composable
fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(8)) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Asks for the PIN; calls [onCorrect] only when it matches. */
@Composable
fun EnterPinDialog(title: String, message: String, onDismiss: () -> Unit, onCorrect: () -> Unit) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val submit = {
        if (Pin.check(ctx, pin)) {
            onCorrect()
        } else {
            wrong = true
            pin = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                PinField(
                    value = pin,
                    onValueChange = { pin = it; wrong = false },
                    label = "PIN",
                    modifier = Modifier.focusRequester(focus),
                    isError = wrong,
                    onDone = { if (pin.isNotEmpty()) submit() },
                )
                if (wrong) Text("Wrong PIN, try again", color = MaterialTheme.colorScheme.error)
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = pin.isNotEmpty()) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SetPinDialog(requireCurrent: Boolean, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (requireCurrent) "Change PIN" else "Set your PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("You'll type this PIN when you tap \"I'm up\" to silence the rest of a category's alarms.")
                if (requireCurrent) PinField(current, { current = it; error = null }, "Current PIN")
                PinField(newPin, { newPin = it; error = null }, "New PIN (4–8 digits)")
                PinField(confirm, { confirm = it; error = null }, "Repeat new PIN")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    requireCurrent && !Pin.check(ctx, current) -> "Current PIN is wrong"
                    newPin.length < 4 -> "Use at least 4 digits"
                    newPin != confirm -> "The PINs don't match"
                    else -> null
                }
                if (error == null) {
                    Pin.set(ctx, newPin)
                    onSaved()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Single text field dialog, used to name categories. */
@Composable
fun NameDialog(title: String, initial: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                placeholder = { Text("e.g. Morning, Nap, Gym") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name.trim()) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            LaunchedEffect(Unit) { focus.requestFocus() }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
