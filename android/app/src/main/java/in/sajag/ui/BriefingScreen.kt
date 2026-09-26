package `in`.sajag.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import `in`.sajag.assess.Modules
import `in`.sajag.credential.Supervisor
import `in`.sajag.data.Ulid
import `in`.sajag.i18n.S
import `in`.sajag.i18n.t
import `in`.sajag.ui.theme.DangerText
import `in`.sajag.ui.theme.Muted
import `in`.sajag.ui.theme.Navy
import `in`.sajag.ui.theme.SafeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Before every drill: how the drill will look on this phone, a check that the
 * worker is standing somewhere safe, and the supervisor's sign-off that this
 * worker is the one taking the attempt.
 *
 * The attempt id is created here, not when the drill ends, because it is what
 * the supervisor signs. A new visit to this screen is a new attempt.
 */
@Composable
fun BriefingScreen(
    moduleId: String,
    app: AppState,
    onBack: () -> Unit,
    onStart: (attemptId: String, supervisorSig: String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = app.profile
    val attemptId = remember { Ulid.next() }
    val supervisor = app.supervisor
    val content = remember(moduleId) { Modules.content(moduleId) }
    var safeArea by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var signature by remember { mutableStateOf<String?>(null) }
    var wrongPin by remember { mutableStateOf(false) }
    var signing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { app.refreshAr() }

    // The drill asks for the camera itself; without one it falls back to a drawn gallery.
    val mode = if (app.arAvailable) DrillMode.AR else DrillMode.CAMERA

    Page(title = t(S.briefingTitle), onBack = onBack, subtitle = t(content.title)) {
        SectionCard {
            CardTitle(mode.icon, "${t(S.howItLooks)}: ${t(mode.label)}")
            Text(t(if (mode == DrillMode.AR) S.briefAr else S.briefCamera))
            Text("${content.beats.size} ${t(S.steps)} · ${content.minutes} ${t(S.minutes)}", color = Muted)
        }

        SectionCard {
            CardTitle(Ic.Warning, t(S.safeAreaTitle))
            Text(t(S.safeAreaBody))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { safeArea = !safeArea },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = safeArea, onCheckedChange = { safeArea = it }, colors = CheckboxDefaults.colors(checkedColor = Navy))
                Text(t(S.safeAreaConfirm), fontWeight = FontWeight.Bold)
            }
        }

        SectionCard {
            CardTitle(Ic.SupervisorAccount, t(S.supervisorTitle))
            Text(t(S.supervisorBody), color = Muted)
            val signed = signature
            when {
                supervisor == null -> Banner(Ic.Info, t(S.noSupervisorSetUp), Tone.NEUTRAL)
                signed != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.Verified, contentDescription = null, tint = SafeText, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${t(S.signedBy)} ${supervisor.name}", color = SafeText, fontWeight = FontWeight.Bold)
                }
                else -> {
                    Text(supervisor.name, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { v -> pin = v.filter { it.isDigit() }.take(8); wrongPin = false },
                        label = { Text(t(S.supervisorPin)) },
                        singleLine = true,
                        isError = wrongPin,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (wrongPin) Text(t(S.wrongPin), color = DangerText)
                    if (app.demoMode && Supervisor.isDemo(context)) Text(t(S.demoPinHint), color = Muted)
                    SecondaryButton(
                        t(if (signing) S.working else S.signOff),
                        icon = Ic.Lock,
                        enabled = !signing && Supervisor.isValidPin(pin),
                    ) {
                        signing = true
                        scope.launch {
                            val result = withContext(Dispatchers.Default) {
                                Supervisor.coSign(context, pin, attemptId, profile.workerHex)
                            }
                            signing = false
                            pin = ""
                            if (result == null) wrongPin = true else signature = result
                        }
                    }
                }
            }
        }

        BigButton(
            t(if (signature != null) S.startDrill else S.practiseWithout),
            icon = Ic.PlayArrow,
            enabled = safeArea && !signing,
        ) { onStart(attemptId, signature) }
    }
}
