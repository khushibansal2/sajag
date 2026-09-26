package `in`.sajag.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.sajag.assess.Modules
import `in`.sajag.i18n.LocalLang
import `in`.sajag.i18n.S
import `in`.sajag.i18n.t
import `in`.sajag.ui.theme.Danger
import `in`.sajag.ui.theme.DangerText
import `in`.sajag.ui.theme.Navy

/**
 * The phone's torch, for a dark gallery. Keeps [on] in step with the system,
 * so the button is right even if the torch was switched from quick settings.
 */
private class Torch(context: Context) {
    private val manager: CameraManager? = context.getSystemService(CameraManager::class.java)
    val cameraId: String? = runCatching {
        manager?.cameraIdList?.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()
    var on by mutableStateOf(false)
        private set

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) on = enabled
        }
    }

    fun start() = runCatching { manager?.registerTorchCallback(callback, Handler(Looper.getMainLooper())) }
    fun stop() = runCatching { manager?.unregisterTorchCallback(callback) }

    fun toggle(): Boolean {
        val id = cameraId ?: return false
        return runCatching { manager?.setTorchMode(id, !on); true }.getOrDefault(false)
    }
}

/** Opens the dialler with [number] filled in. The worker presses call; the app never calls by itself. */
private fun dial(context: Context, number: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.filter { it.isDigit() || it == '+' })))
    true
} catch (e: ActivityNotFoundException) {
    false
}

/**
 * Emergency mode: the control room at one tap, the torch, and what to do,
 * read aloud. It is clearly not an alarm: the site alarm and the supervisor
 * come first, and the screen says so at the top.
 */
@Composable
fun EmergencyScreen(app: AppState, onBack: () -> Unit, onReport: (String) -> Unit) {
    val context = LocalContext.current
    val lang = LocalLang.current
    val notify = LocalNotify.current
    val guide = rememberGuide()
    val torch = remember { Torch(context) }
    DisposableEffect(torch) {
        torch.start()
        onDispose { torch.stop() }
    }
    var selected by remember { mutableIntStateOf(0) }
    val emergency = Modules.EMERGENCIES[selected]

    Page(title = t(S.emergency), onBack = onBack, subtitle = t(S.emergencySubtitle)) {
        Banner(Ic.Warning, t(S.emergencyNotAlarm), Tone.DANGER, title = t(S.notAnAlarm))

        if (app.controlRoom.isNotBlank()) {
            BigButton("${t(S.callControlRoom)} · ${app.controlRoom}", icon = Ic.Call, color = Danger, contentColor = Color.White) {
                if (!dial(context, app.controlRoom)) notify(S.noDialler.of(lang))
            }
        } else {
            Banner(Ic.Info, t(S.noControlRoom), Tone.NEUTRAL)
        }
        SecondaryButton(t(S.call112), icon = Ic.Call, color = DangerText) {
            if (!dial(context, "112")) notify(S.noDialler.of(lang))
        }
        if (torch.cameraId != null) {
            SecondaryButton(t(if (torch.on) S.torchOff else S.torchOn), icon = if (torch.on) Ic.FlashlightOff else Ic.FlashlightOn) {
                if (!torch.toggle()) notify(S.torchFailed.of(lang))
            }
        }
        if (app.assemblyPoint.isNotBlank()) {
            Banner(Ic.Place, app.assemblyPoint, Tone.INFO, title = t(S.assemblyPoint))
        }

        SectionHeader(t(S.whatToDo))
        Segmented(Modules.EMERGENCIES.map { t(it.title) }, selected, onSelect = {
            selected = it
            guide.stop()
        })
        SectionCard {
            emergency.steps.forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(30.dp).background(if (i == 0) Danger else Navy, CircleShape), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(t(step), style = MaterialTheme.typography.bodyLarge, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(t(S.readAloud), icon = Ic.VolumeUp, modifier = Modifier.weight(1f)) {
                    guide.say(emergency.steps, lang)
                }
                SecondaryButton(t(S.stopReading), icon = Ic.Stop, modifier = Modifier.weight(1f)) { guide.stop() }
            }
        }
        SecondaryButton(t(S.reportThis), icon = Ic.Report) { onReport(emergency.reportType) }
    }
}
