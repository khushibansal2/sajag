package `in`.sajag.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import `in`.sajag.SajagApp
import `in`.sajag.assess.AttemptResult
import `in`.sajag.assess.ScoringEvent
import `in`.sajag.credential.DeviceIdentity
import `in`.sajag.i18n.LocalLang
import `in`.sajag.i18n.S
import `in`.sajag.i18n.T
import `in`.sajag.i18n.t
import `in`.sajag.ui.theme.AmberBg
import `in`.sajag.ui.theme.Bg
import `in`.sajag.ui.theme.Card
import `in`.sajag.ui.theme.Muted
import `in`.sajag.ui.theme.Navy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Everything the result screen needs from a finished drill. Held in memory only. */
data class CompletedRun(
    val moduleId: String,
    val attemptId: String,
    /** The mode the attempt counts as (1 AR, 2 camera, 3 guided): the most conservative one used. */
    val tier: Int,
    val events: List<ScoringEvent>,
    val result: AttemptResult,
    val supervisorSig: String?,
)

sealed interface Screen {
    // The four tabs.
    data object Home : Screen
    data object Passport : Screen
    data object Report : Screen
    data object Verify : Screen

    // Screens opened on top of a tab. They have a back arrow and no tab bar.
    data class Briefing(val moduleId: String) : Screen
    data class Drill(val moduleId: String, val attemptId: String, val supervisorSig: String?) : Screen
    data class Result(val run: CompletedRun) : Screen
    data class Certificate(val qr: String) : Screen
    data object Settings : Screen
    data object WorkerList : Screen
    data class WorkerForm(val workerHex: String?) : Screen
    data object SupervisorSetup : Screen
    data object Emergency : Screen
    data object RiskMap : Screen
    data object TrainingCentre : Screen
}

private val TAB_ROOTS = listOf(Screen.Home, Screen.Passport, Screen.Report, Screen.Verify)

private data class TabItem(val root: Screen, val label: T, val icon: ImageVector)

/** Shows a short message at the bottom of the screen, from anywhere in the app. */
val LocalNotify = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
fun SajagRoot() {
    val context = LocalContext.current
    val app = remember { AppState(context) }
    val db = remember { (context.applicationContext as SajagApp).db }
    val trust = remember { DeviceIdentity.trustList(context) }
    val pendingSync by db.attempts().unsyncedCount().collectAsState(initial = 0)
    val wallet by db.credentials().wallet().collectAsState(initial = emptyList())
    val today = remember { LocalDate.now() }
    val profile = app.profile
    val held = remember(wallet, profile.workerHex) { heldCertificates(wallet, trust, profile.workerHex, today) }
    var showSplash by remember { mutableStateOf(true) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = remember {
        { message -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(message) } }
    }

    // ARCore can answer "checking" at start-up, and an install happens outside the app: ask again.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) app.refreshAr() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        delay(2_500)
        app.refreshAr()
    }

    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var forward by remember { mutableStateOf(true) }
    fun push(s: Screen) {
        // A double tap during the slide-in must not open the same screen twice.
        if (stack.last() == s) return
        forward = true
        stack.add(s)
    }
    fun pop() {
        forward = false
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
    /** Back from [s]; nothing if [s] is already leaving, so a double tap on a back arrow goes back once. */
    fun popFrom(s: Screen) {
        if (stack.last() == s) pop()
    }
    fun resetTo(vararg screens: Screen, forwards: Boolean = screens.size > 1) {
        forward = forwards
        stack.clear()
        screens.forEach { stack.add(it) }
    }
    fun replaceTop(s: Screen) {
        forward = true
        stack[stack.lastIndex] = s
    }

    val current = stack.last()

    // Back walks the stack; from another tab's root it returns to Home.
    BackHandler(enabled = app.onboarded && !showSplash && (stack.size > 1 || current != Screen.Home)) {
        if (stack.size > 1) pop() else resetTo(Screen.Home)
    }

    // Dark status bar icons on the light screens, light ones over the drill's
    // camera. The navigation bar keeps dark icons: in the drill it sits over
    // the white answer sheet.
    val view = LocalView.current
    val darkScreen = !showSplash && app.onboarded && current is Screen.Drill
    LaunchedEffect(darkScreen) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkScreen
            isAppearanceLightNavigationBars = true
        }
    }

    val tabs = remember {
        listOf(
            TabItem(Screen.Home, S.tabHome, Ic.Home),
            TabItem(Screen.Passport, S.tabPassport, Ic.Badge),
            TabItem(Screen.Report, S.tabReport, Ic.Report),
            TabItem(Screen.Verify, S.tabVerify, Ic.QrCodeScanner),
        )
    }

    CompositionLocalProvider(LocalLang provides app.lang, LocalNotify provides notify) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            when {
                showSplash -> SplashScreen(onDone = { showSplash = false })
                !app.onboarded -> OnboardingScreen(app)
                else -> AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        val tabSwitch = initialState in TAB_ROOTS && targetState in TAB_ROOTS
                        // The drill is a camera surface; it fades rather than slides.
                        val camera = initialState is Screen.Drill || targetState is Screen.Drill
                        when {
                            tabSwitch || camera -> fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                            forward -> (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)))
                                .togetherWith(slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(250)))
                            else -> (slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300)))
                                .togetherWith(slideOutHorizontally(tween(300)) { it } + fadeOut(tween(250)))
                        }.apply { targetContentZIndex = if (forward || tabSwitch) 1f else -1f }
                    },
                    label = "screens",
                ) { screen ->
                    when (screen) {
                        // Tab roots always sit at the bottom of the stack, so they always carry the tab bar.
                        Screen.Home, Screen.Passport, Screen.Report, Screen.Verify -> TabHost(
                            tabs,
                            selected = TAB_ROOTS.indexOf(screen),
                            onSelect = { resetTo(tabs[it].root) },
                        ) {
                            when (screen) {
                                Screen.Home -> HomeScreen(
                                    app = app,
                                    held = held,
                                    pendingSync = pendingSync,
                                    onOpenModule = { push(Screen.Briefing(it)) },
                                    onEmergency = { push(Screen.Emergency) },
                                    onSettings = { push(Screen.Settings) },
                                    onWorkers = { push(Screen.WorkerList) },
                                    onTrainingCentre = { push(Screen.TrainingCentre) },
                                    onRiskMap = { push(Screen.RiskMap) },
                                    onSupervisor = { push(Screen.SupervisorSetup) },
                                    onPassport = { resetTo(Screen.Passport) },
                                )
                                Screen.Passport -> PassportScreen(
                                    profile = profile,
                                    held = held,
                                    onOpenCertificate = { push(Screen.Certificate(it)) },
                                    onPractise = { module -> push(Screen.Briefing(module)) },
                                )
                                Screen.Report -> ReportScreen(app)
                                else -> VerifyScreen()
                            }
                        }
                        is Screen.Briefing -> BriefingScreen(
                            moduleId = screen.moduleId,
                            app = app,
                            onBack = { popFrom(screen) },
                            onStart = { attemptId, sig -> replaceTop(Screen.Drill(screen.moduleId, attemptId, sig)) },
                        )
                        is Screen.Drill -> DrillScreen(
                            moduleId = screen.moduleId,
                            attemptId = screen.attemptId,
                            supervisorSig = screen.supervisorSig,
                            tierCeiling = app.capability.ceiling,
                            arAvailable = app.arAvailable,
                            profile = profile,
                            onExit = { popFrom(screen) },
                            onFinish = { run -> replaceTop(Screen.Result(run)) },
                        )
                        is Screen.Result -> Box(Modifier.fillMaxSize()) {
                            ResultScreen(
                                run = screen.run,
                                profile = profile,
                                onCertificate = { qr -> resetTo(Screen.Passport, Screen.Certificate(qr)) },
                                onReplay = { replaceTop(Screen.Briefing(screen.run.moduleId)) },
                                onHome = { resetTo(Screen.Home) },
                            )
                            if (screen.run.result.passed) Confetti()
                        }
                        is Screen.Certificate -> CertificateScreen(qr = screen.qr, onBack = { popFrom(screen) })
                        Screen.Settings -> SettingsScreen(
                            app = app,
                            onBack = { popFrom(screen) },
                            onWorkers = { push(Screen.WorkerList) },
                            onEditWorker = { push(Screen.WorkerForm(it)) },
                            onSupervisor = { push(Screen.SupervisorSetup) },
                        )
                        Screen.WorkerList -> WorkerListScreen(
                            app = app,
                            onBack = { popFrom(screen) },
                            onAdd = { push(Screen.WorkerForm(null)) },
                            onEdit = { push(Screen.WorkerForm(it)) },
                        )
                        is Screen.WorkerForm -> WorkerFormScreen(app = app, workerHex = screen.workerHex, onDone = { popFrom(screen) })
                        Screen.SupervisorSetup -> SupervisorScreen(app = app, onBack = { popFrom(screen) })
                        Screen.Emergency -> EmergencyScreen(
                            app = app,
                            onBack = { popFrom(screen) },
                            onReport = { type ->
                                app.reportPrefill = type
                                resetTo(Screen.Report, forwards = true)
                            },
                        )
                        Screen.RiskMap -> RiskMapScreen(app = app, onBack = { popFrom(screen) })
                        Screen.TrainingCentre -> TrainingCentreScreen(app = app, onBack = { popFrom(screen) })
                    }
                }
            }

            val tabsShowing = app.onboarded && !showSplash && current in TAB_ROOTS
            SnackbarHost(
                snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(bottom = if (tabsShowing) 80.dp else 8.dp, start = 8.dp, end = 8.dp),
            )
        }
    }
}

/** The four tabs and the bar under them. The bar hides while the keyboard is up. */
@Composable
private fun TabHost(tabs: List<TabItem>, selected: Int, onSelect: (Int) -> Unit, content: @Composable () -> Unit) {
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        if (!imeVisible) {
            NavigationBar(containerColor = Card, tonalElevation = 0.dp) {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = i == selected,
                        onClick = { if (i != selected) onSelect(i) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(t(tab.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Navy,
                            selectedTextColor = Navy,
                            indicatorColor = AmberBg,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted,
                        ),
                    )
                }
            }
        }
    }
}
