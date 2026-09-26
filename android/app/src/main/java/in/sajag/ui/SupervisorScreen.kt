package `in`.sajag.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import `in`.sajag.credential.Supervisor
import `in`.sajag.credential.SupervisorInfo
import `in`.sajag.i18n.LocalLang
import `in`.sajag.i18n.S
import `in`.sajag.i18n.t
import `in`.sajag.ui.theme.Danger
import `in`.sajag.ui.theme.DangerText
import `in`.sajag.ui.theme.Muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Set up the supervisor who signs off drills on this (shared) phone. The key
 * id shown here is what the training centre registers, so the server can tell
 * a real supervisor's sign-off from anyone else's. Replacing or removing a
 * supervisor needs the current PIN.
 */
@Composable
fun SupervisorScreen(app: AppState, onBack: () -> Unit) {
    val onChanged: (SupervisorInfo?) -> Unit = app::onSupervisorChanged
    val context = LocalContext.current
    val lang = LocalLang.current
    val notify = LocalNotify.current
    var current by remember { mutableStateOf(Supervisor.current(context)) }
    var unlocked by remember { mutableStateOf(current == null) }
    var askPin by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val pinOk = Supervisor.isValidPin(pin)
    val match = pin == pin2

    Page(title = t(S.supervisorSection), onBack = onBack) {
        val existing = current
        if (existing != null) {
            SectionCard {
                CardTitle(Ic.SupervisorAccount, existing.name)
                Text(t(S.supervisorKey), color = Muted)
                Text(existing.keyId, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text(t(S.supervisorKeyHelp), color = Muted, style = MaterialTheme.typography.bodySmall)
                if (Supervisor.isDemo(context)) Banner(Ic.Info, t(S.demoPinHint), Tone.WARN)
            }
        }

        if (existing != null && !unlocked) {
            SecondaryButton(t(S.changeSupervisor), icon = Ic.Lock) { askPin = true }
        } else {
            SectionCard {
                CardTitle(Ic.Lock, t(if (existing == null) S.setUpSupervisor else S.changeSupervisor))
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) },
                    label = { Text(t(S.supervisorName)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pin, onValueChange = { v -> pin = v.filter { it.isDigit() }.take(8) },
                    label = { Text(t(S.newPin)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pin2, onValueChange = { v -> pin2 = v.filter { it.isDigit() }.take(8) },
                    label = { Text(t(S.repeatPin)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (pin.isNotEmpty() && !pinOk) Text(t(S.pinRule), color = DangerText)
                if (pin2.isNotEmpty() && !match) Text(t(S.pinMismatch), color = DangerText)
                BigButton(
                    t(if (saving) S.working else S.save),
                    icon = Ic.Check,
                    enabled = !saving && name.isNotBlank() && pinOk && match,
                ) {
                    saving = true
                    // The app's scope: the key derivation takes a moment, and leaving
                    // this screen meanwhile must not leave the app unaware of the new supervisor.
                    app.scope.launch {
                        val info = withContext(Dispatchers.Default) { Supervisor.setUp(context, name, pin) }
                        saving = false
                        name = ""
                        pin = ""
                        pin2 = ""
                        current = info
                        unlocked = false
                        onChanged(info)
                        notify(S.supervisorSaved.of(lang))
                    }
                }
            }
            if (existing != null) {
                SecondaryButton(t(S.removeSupervisor), icon = Ic.Close, color = DangerText) { confirmRemove = true }
            }
        }
    }

    if (askPin) {
        PinDialog(
            title = t(S.supervisorPin),
            body = t(S.pinToChange),
            check = { Supervisor.checkPin(context, it) },
            onOk = {
                askPin = false
                unlocked = true
            },
            onDismiss = { askPin = false },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            icon = { Icon(Ic.Warning, contentDescription = null, tint = Danger) },
            title = { Text(t(S.removeSupervisor)) },
            text = { Text(t(S.removeSupervisorBody)) },
            confirmButton = {
                TextButton(onClick = {
                    Supervisor.remove(context)
                    current = null
                    unlocked = true
                    confirmRemove = false
                    onChanged(null)
                }) { Text(t(S.remove), color = DangerText, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(t(S.cancel)) } },
        )
    }
}
