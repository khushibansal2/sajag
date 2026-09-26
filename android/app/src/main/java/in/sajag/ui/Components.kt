package `in`.sajag.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.sajag.assess.Opt
import `in`.sajag.geo.Jharkhand
import `in`.sajag.i18n.S
import `in`.sajag.i18n.t
import `in`.sajag.ui.theme.Amber
import `in`.sajag.ui.theme.AmberBg
import `in`.sajag.ui.theme.AmberText
import `in`.sajag.ui.theme.Bg
import `in`.sajag.ui.theme.Card
import `in`.sajag.ui.theme.CardAlt
import `in`.sajag.ui.theme.DangerBg
import `in`.sajag.ui.theme.DangerText
import `in`.sajag.ui.theme.InfoBg
import `in`.sajag.ui.theme.InfoText
import `in`.sajag.ui.theme.Ink
import `in`.sajag.ui.theme.Line
import `in`.sajag.ui.theme.Muted
import `in`.sajag.ui.theme.Navy
import `in`.sajag.ui.theme.SafeBg
import `in`.sajag.ui.theme.SafeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * For a layer drawn over other controls (a STOP card, a "please wait" cover):
 * taps land on this layer instead of passing through to the buttons beneath.
 */
fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {}

// ---------------------------------------------------------------------- page structure

/**
 * The top bar every inner screen shares: a back arrow when there is somewhere
 * to go back to, the title, and optional actions on the right. Sits under the
 * status bar, which the app draws edge to edge.
 */
@Composable
fun AppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(color = Bg, contentColor = Ink) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 64.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(52.dp)) {
                    Icon(Ic.ArrowBack, contentDescription = t(S.back), tint = Ink)
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            actions()
        }
    }
}

/**
 * A standard screen: [AppBar] and a scrolling column of cards. [insetBottom]
 * keeps the last card above the gesture bar on screens without the tab bar.
 */
@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    insetBottom: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Bg)) {
        AppBar(title, onBack, subtitle, actions)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
                .then(if (insetBottom) Modifier.navigationBarsPadding() else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** A small, spaced-out heading above a group of cards. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = Muted,
        modifier = modifier.padding(top = 6.dp, start = 4.dp),
    )
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Card,
        contentColor = Ink,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Line),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** A heading row inside a card: icon plus bold title. */
@Composable
fun CardTitle(icon: ImageVector, text: String, tint: Color = Navy) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

// ---------------------------------------------------------------------- buttons

/** Big, glove-friendly primary action: at least 60 dp tall, full width. Amber with black text. */
@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Amber,
    contentColor: Color = Color.Black,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 60.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = contentColor,
            disabledContainerColor = CardAlt,
            disabledContentColor = Muted,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** The second choice on a screen: outlined, navy, same height as [BigButton]. */
@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = Navy,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, if (enabled) color else Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------------- rows and chips

/** A tappable row, as in a phone's own settings: icon, title, optional subtitle, chevron. */
@Composable
fun ListRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = Navy,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        if (trailing != null) trailing()
        else if (onClick != null) Icon(Ic.ChevronRight, contentDescription = null, tint = Muted)
    }
}

@Composable
fun SwitchRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListRow(icon, title, subtitle, onClick = { onChange(!checked) }, trailing = {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Navy, checkedThumbColor = Color.White),
        )
    })
}

@Composable
fun RowDivider() = HorizontalDivider(color = Line, modifier = Modifier.padding(start = 58.dp))

enum class Tone { INFO, WARN, DANGER, SAFE, NEUTRAL }

private fun toneColors(tone: Tone): Pair<Color, Color> = when (tone) {
    Tone.INFO -> InfoText to InfoBg
    Tone.WARN -> AmberText to AmberBg
    Tone.DANGER -> DangerText to DangerBg
    Tone.SAFE -> SafeText to SafeBg
    Tone.NEUTRAL -> Muted to CardAlt
}

/** A small rounded label: a status, a mode, a count. */
@Composable
fun StatusChip(text: String, tone: Tone, icon: ImageVector? = null) {
    val (fg, bg) = toneColors(tone)
    Row(
        Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** A coloured note across the width of the screen. */
@Composable
fun Banner(icon: ImageVector, text: String, tone: Tone, modifier: Modifier = Modifier, title: String? = null) {
    val (fg, bg) = toneColors(tone)
    Row(
        modifier.fillMaxWidth().background(bg, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            if (title != null) Text(title, color = fg, fontWeight = FontWeight.Bold)
            Text(text, color = if (title != null) Ink else fg, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Shown on every screen that shows made-up demo data, so nobody mistakes it for real. */
@Composable
fun SampleDataBanner() = Banner(Ic.Info, t(S.sampleBody), Tone.WARN, title = t(S.sampleTitle))

/** Two or three choices side by side, one selected. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().background(CardAlt, RoundedCornerShape(14.dp)).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Card else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Ink else Muted)
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(72.dp).background(CardAlt, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(36.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(body, color = Muted, textAlign = TextAlign.Center)
    }
}

/** A round badge with a person's initials. */
@Composable
fun Avatar(name: String, size: Dp = 44.dp, background: Color = Navy, content: Color = Color.White) {
    val initials = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
    Box(Modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Text(initials, color = content, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
    }
}

@Composable
fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier, tint: Color = Navy) {
    Surface(modifier, color = Card, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Ink)
            Text(label, fontSize = 12.sp, color = Muted, maxLines = 2)
        }
    }
}

/** A thin horizontal bar, for scores and rates. */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(CardAlt)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(50)).background(color))
    }
}

// ---------------------------------------------------------------------- drill options

/**
 * A picture-first option: the icon carries the meaning for a worker who cannot
 * read the label. Quiz answers use a letter (A, B, C) instead of an icon.
 */
@Composable
fun OptionCard(opt: Opt, selected: Boolean, badge: String? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        color = if (selected) AmberBg else CardAlt,
        contentColor = Ink,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, if (selected) Amber else Color.Transparent),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = Ic.forKey(opt.icon)
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Navy, modifier = Modifier.size(34.dp))
            } else if (opt.icon.isNotBlank()) {
                Box(
                    Modifier.size(34.dp).background(Card, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text(opt.icon, color = Navy, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            }
            Text(
                t(opt.label),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            if (badge != null) {
                Box(
                    Modifier.size(34.dp).background(Amber, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(badge, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            }
        }
    }
}

// ---------------------------------------------------------------------- dialogs and fields

/** A read-only field that opens the district list. */
@Composable
fun DistrictField(districtId: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val name = Jharkhand.byId(districtId)?.let { t(it.name) }
    Surface(
        onClick = { open = true },
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        color = Card,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, Color(0xFF8A96A3)),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.LocationCity, contentDescription = null, tint = Muted)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(t(S.district), style = MaterialTheme.typography.bodySmall, color = Muted)
                Text(name ?: t(S.chooseDistrict), color = if (name != null) Ink else Muted, style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Ic.KeyboardArrowDown, contentDescription = null, tint = Muted)
        }
    }
    if (open) {
        DistrictDialog(districtId, onPick = { onChange(it); open = false }, onDismiss = { open = false })
    }
}

@Composable
fun DistrictDialog(selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val districts = remember { Jharkhand.districts.sortedBy { it.name.en } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t(S.chooseDistrict)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                districts.forEach { d ->
                    val on = d.id == selected
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (on) AmberBg else Color.Transparent)
                            .clickable { onPick(d.id) }
                            .heightIn(min = 48.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t(d.name), modifier = Modifier.weight(1f), fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                        if (on) Icon(Ic.Check, contentDescription = null, tint = Navy)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t(S.cancel)) } },
    )
}

/**
 * Asks for the supervisor's PIN before a protected change. [check] runs off
 * the main thread (the PIN check is deliberately slow) and returns true for
 * the right PIN; the dialog stays open and says so otherwise.
 */
@Composable
fun PinDialog(title: String, body: String, check: suspend (String) -> Boolean, onOk: () -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Ic.Lock, contentDescription = null, tint = Navy) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(body, color = Muted)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter { it.isDigit() }.take(8); wrong = false },
                    label = { Text(t(S.supervisorPin)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = wrong,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (wrong) Text(t(S.wrongPin), color = DangerText)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    checking = true
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) { check(pin) }
                        checking = false
                        if (ok) onOk() else {
                            wrong = true
                            pin = ""
                        }
                    }
                },
                enabled = pin.length >= 4 && !checking,
            ) {
                Text(t(if (checking) S.working else S.confirm), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t(S.cancel)) } },
    )
}

/** A short text setting edited in a dialog, e.g. the control room number. */
@Composable
fun TextSettingDialog(
    title: String,
    label: String,
    initial: String,
    phone: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(if (phone) 20 else 80) },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (phone) KeyboardType.Phone else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text(t(S.save), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t(S.cancel)) } },
    )
}
