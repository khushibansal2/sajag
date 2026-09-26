package `in`.sajag.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import `in`.sajag.R
import `in`.sajag.data.Workers
import `in`.sajag.i18n.Lang
import `in`.sajag.i18n.S
import `in`.sajag.i18n.t
import `in`.sajag.ui.theme.Amber
import `in`.sajag.ui.theme.AmberBg
import `in`.sajag.ui.theme.Bg
import `in`.sajag.ui.theme.Card
import `in`.sajag.ui.theme.Line
import `in`.sajag.ui.theme.Muted
import `in`.sajag.ui.theme.Navy

/**
 * First launch: language, who is training, and what the camera is for.
 * Three short steps; everything can be changed later in Settings.
 */
@Composable
fun OnboardingScreen(app: AppState) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    val existing = app.worker
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var employer by remember { mutableStateOf(existing?.employer ?: "") }
    var district by remember { mutableStateOf(existing?.district ?: "") }
    var site by remember { mutableStateOf(existing?.site ?: "") }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }

    fun go(to: Int) {
        forward = to > step
        step = to
    }
    BackHandler(enabled = step > 0) { go(step - 1) }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) {
                IconButton(onClick = { go(step - 1) }, modifier = Modifier.size(52.dp)) {
                    Icon(Ic.ArrowBack, contentDescription = t(S.back))
                }
            } else {
                Spacer(Modifier.width(52.dp))
            }
            Spacer(Modifier.weight(1f))
            // Where the worker is in the three steps.
            repeat(3) { i ->
                Box(
                    Modifier.padding(horizontal = 3.dp).size(width = if (i == step) 22.dp else 8.dp, height = 8.dp)
                        .background(if (i <= step) Navy else Line, RoundedCornerShape(50)),
                )
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(52.dp))
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (forward) {
                    (slideInHorizontally(tween(280)) { it } + fadeIn()) togetherWith (slideOutHorizontally(tween(280)) { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally(tween(280)) { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally(tween(280)) { it } + fadeOut())
                }
            },
            modifier = Modifier.weight(1f),
            label = "onboarding",
        ) { page ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (page) {
                    0 -> WelcomeStep(app.lang, app::setLanguage) { go(1) }
                    1 -> {
                        StepTitle(t(S.aboutYou), t(S.aboutYouBody))
                        OutlinedTextField(
                            value = name, onValueChange = { name = it.take(40) },
                            label = { Text(t(S.yourName)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = employer, onValueChange = { employer = it.take(12) },
                            label = { Text(t(S.employerCode)) }, singleLine = true,
                            supportingText = { Text(t(S.employerHelp)) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DistrictField(district, onChange = { district = it })
                        OutlinedTextField(
                            value = site, onValueChange = { site = it.take(60) },
                            label = { Text(t(S.siteName)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        BigButton(
                            t(S.next),
                            enabled = Workers.cleanName(name).isNotBlank() && Workers.cleanEmployer(employer).isNotBlank() && district.isNotBlank(),
                        ) { go(2) }
                    }
                    else -> {
                        Box(
                            Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp).size(112.dp).background(AmberBg, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Ic.ViewInAr, contentDescription = null, tint = Navy, modifier = Modifier.size(60.dp)) }
                        StepTitle(t(S.cameraTitle), t(S.cameraBody), center = true)
                        Banner(Ic.Shield, t(S.cameraPrivacy), Tone.INFO)
                        Banner(Ic.Info, t(S.emergencyNotAlarm), Tone.WARN)
                        if (!cameraGranted) {
                            BigButton(t(S.allowCamera), icon = Ic.PhotoCamera) { permission.launch(Manifest.permission.CAMERA) }
                            SecondaryButton(t(S.notNow)) {
                                app.finishOnboarding(Workers.cleanName(name), Workers.cleanEmployer(employer), district, site.trim())
                            }
                        } else {
                            BigButton(t(S.startUsing), icon = Ic.Check) {
                                app.finishOnboarding(Workers.cleanName(name), Workers.cleanEmployer(employer), district, site.trim())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.StepTitle(title: String, body: String, center: Boolean = false) {
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = if (center) Modifier.align(Alignment.CenterHorizontally) else Modifier,
    )
    Text(
        body,
        color = Muted,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = if (center) Modifier.align(Alignment.CenterHorizontally) else Modifier,
    )
}

@Composable
private fun ColumnScope.WelcomeStep(lang: Lang, onLang: (Lang) -> Unit, onNext: () -> Unit) {
    Box(
        Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).size(120.dp).background(Navy, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(painter = painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(140.dp))
    }
    Text(
        t(S.welcome),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    Text(t(S.tagline), color = Muted, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally))
    SectionHeader(t(S.chooseLanguage))
    Lang.entries.forEach { l ->
        val on = l == lang
        Surface(
            onClick = { onLang(l) },
            color = if (on) AmberBg else Card,
            border = BorderStroke(if (on) 2.dp else 1.dp, if (on) Amber else Line),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Language, contentDescription = null, tint = Navy)
                Text(l.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
                if (on) Icon(Ic.CheckCircle, contentDescription = null, tint = Navy)
            }
        }
    }
    if (lang == Lang.SAT) Text(t(S.santaliNotice), color = Muted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(4.dp))
    BigButton(t(S.next), onClick = onNext)
}
