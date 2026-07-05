package com.example.voicetodo.ui

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.voicetodo.data.ActionType
import com.example.voicetodo.data.AppResolver
import com.example.voicetodo.notify.NotificationHelper
import com.example.voicetodo.ui.theme.VoiceTodoTheme
import kotlinx.coroutines.delay

/**
 * Full-screen screen shown when an action task is due (call / WhatsApp / open app / navigate).
 * Wakes the phone, counts down 5s, then performs the action (cancellable).
 */
class ActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val action = runCatching { ActionType.valueOf(intent.getStringExtra(EXTRA_ACTION) ?: "NONE") }
            .getOrDefault(ActionType.NONE)
        val name = intent.getStringExtra(EXTRA_NAME) ?: "Contact"
        val number = intent.getStringExtra(EXTRA_NUMBER)
        val arg = intent.getStringExtra(EXTRA_ARG)
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        if (notifId >= 0) NotificationHelper.cancel(this, notifId)

        val error = validate(action, name, number, arg)

        setContent {
            VoiceTodoTheme {
                ActionScreen(
                    headline = headline(action, name, arg),
                    subtitle = number ?: arg,
                    error = error,
                    onRun = { perform(action, name, number, arg); finish() },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun validate(action: ActionType, name: String, number: String?, arg: String?): String? = when (action) {
        ActionType.CALL, ActionType.WHATSAPP ->
            if (number.isNullOrBlank()) "No contact named \"$name\" was found in your phone." else null
        ActionType.OPEN_APP ->
            if (AppResolver.launchIntent(this, arg ?: "") == null) "App \"$arg\" isn't installed." else null
        ActionType.NAVIGATE ->
            if (arg.isNullOrBlank()) "No destination was understood." else null
        else -> null
    }

    private fun headline(action: ActionType, name: String, arg: String?): String = when (action) {
        ActionType.CALL -> "Calling ${name.replaceFirstChar { it.uppercase() }}"
        ActionType.WHATSAPP -> "Messaging ${name.replaceFirstChar { it.uppercase() }} on WhatsApp"
        ActionType.OPEN_APP -> "Opening ${arg?.replaceFirstChar { it.uppercase() }}"
        ActionType.NAVIGATE -> "Navigating to ${arg?.replaceFirstChar { it.uppercase() }}"
        else -> "Running task"
    }

    private fun perform(action: ActionType, name: String, number: String?, arg: String?) {
        try {
            when (action) {
                ActionType.CALL -> {
                    if (number.isNullOrBlank()) return
                    val hasPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) ==
                        PackageManager.PERMISSION_GRANTED
                    val act = if (hasPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL
                    startActivity(Intent(act, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                ActionType.WHATSAPP -> {
                    val digits = number?.filter { it.isDigit() || it == '+' }?.trimStart('+')
                    val text = arg?.let { "?text=" + Uri.encode(it) }.orEmpty()
                    val url = if (!digits.isNullOrBlank()) "https://wa.me/$digits$text" else "https://wa.me/"
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                ActionType.OPEN_APP -> {
                    val launch = AppResolver.launchIntent(this, arg ?: "")
                    if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    else toast("Couldn't find app: $arg")
                }
                ActionType.NAVIGATE -> {
                    val place = Uri.encode(arg ?: "")
                    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$place"))
                        .setPackage("com.google.android.apps.maps")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (nav.resolveActivity(packageManager) != null) startActivity(nav)
                    else startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$place"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                else -> {}
            }
        } catch (e: Exception) {
            toast("Couldn't complete action: ${e.message}")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val EXTRA_NAME = "name"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_ARG = "arg"
        const val EXTRA_NOTIF_ID = "notif_id"
    }
}

@Composable
private fun ActionScreen(
    headline: String,
    subtitle: String?,
    error: String?,
    onRun: () -> Unit,
    onCancel: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val ok = error == null
    var seconds by remember { mutableIntStateOf(5) }

    LaunchedEffect(ok) {
        if (!ok) return@LaunchedEffect
        while (seconds > 0) { delay(1000); seconds-- }
        onRun()
    }

    Box(
        Modifier.fillMaxSize().background(scheme.background).padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(110.dp).clip(CircleShape).background(scheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Bolt, null, tint = scheme.onPrimary, modifier = Modifier.size(48.dp))
            }
            Text(
                headline,
                Modifier.padding(top = 24.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (ok) {
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, Modifier.padding(top = 4.dp), color = scheme.onSurfaceVariant)
                }
                Text(
                    "in $seconds…",
                    Modifier.padding(top = 20.dp),
                    fontSize = 18.sp, fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    error!!,
                    Modifier.padding(top = 12.dp),
                    color = scheme.onSurfaceVariant, textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Filled.Close, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (ok) "Cancel" else "Close")
                }
                if (ok) {
                    Button(
                        onClick = onRun,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.primary, contentColor = scheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Filled.Bolt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Do it now")
                    }
                }
            }
        }
    }
}
