package `in`.sajag.ui

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import `in`.sajag.capability.ArSupport
import `in`.sajag.capability.Tier
import `in`.sajag.capability.TierProbe
import `in`.sajag.credential.Supervisor
import `in`.sajag.credential.SupervisorInfo
import `in`.sajag.data.AppSettings
import `in`.sajag.data.Prefs
import `in`.sajag.data.Profile
import `in`.sajag.data.Workers
import `in`.sajag.i18n.Lang
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Everything the screens share: the language, who is training, the phone's
 * supervisor and settings, and what the phone can do. Each change is saved
 * at once and shows up on every screen, because each field is Compose state.
 */
@Stable
class AppState(private val context: Context) {
    var lang by mutableStateOf(Prefs.lang(context))
        private set
    var workers by mutableStateOf(Workers.all(context))
        private set
    var worker by mutableStateOf(Workers.active(context))
        private set
    var supervisor by mutableStateOf(Supervisor.current(context))
        private set
    var controlRoom by mutableStateOf(AppSettings.controlRoom(context))
        private set
    var assemblyPoint by mutableStateOf(AppSettings.assemblyPoint(context))
        private set
    var demoMode by mutableStateOf(AppSettings.demoMode(context))
        private set
    var useAr by mutableStateOf(AppSettings.useAr(context))
        private set
    var capability by mutableStateOf(TierProbe.probe(context))
        private set
    var arSupport by mutableStateOf(ArSupport.check(context))
        private set

    /** First launch shows onboarding. A worker carried over from the first version skips it. */
    var onboarded by mutableStateOf(AppSettings.onboarded(context) || worker?.enrolled == true)
        private set

    /**
     * For work that must finish even if the screen that started it closes,
     * such as the slow PIN derivation behind a new supervisor.
     */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Set by the emergency screen, read once by the report screen. */
    var reportPrefill: String? = null

    /** The worker who is training now. Only blank before onboarding has finished. */
    val profile: Profile get() = worker ?: Profile("", "", "0".repeat(32))

    /** World-anchored AR for the next drill: switched on, supported by the phone, and installed. */
    val arAvailable: Boolean
        get() = useAr && capability.ceiling == Tier.WORLD_ANCHORED && arSupport == ArSupport.READY

    fun setLanguage(value: Lang) {
        lang = value
        Prefs.setLang(context, value)
    }

    fun setArEnabled(on: Boolean) {
        useAr = on
        AppSettings.setUseAr(context, on)
    }

    /** ARCore answers "checking" for a moment after start-up, and changes after an install. Ask again. */
    fun refreshAr() {
        capability = TierProbe.probe(context)
        arSupport = ArSupport.check(context)
    }

    fun updateControlRoom(number: String) {
        AppSettings.setControlRoom(context, number)
        controlRoom = AppSettings.controlRoom(context)
    }

    fun updateAssemblyPoint(text: String) {
        AppSettings.setAssemblyPoint(context, text)
        assemblyPoint = AppSettings.assemblyPoint(context)
    }

    /**
     * Demo mode shows sample data on the training centre and risk map screens,
     * always labelled as sample. If the phone has no supervisor, it also sets up
     * a demo supervisor with PIN 1234, and removes only that one again.
     * The PIN derivation is slow: call from a background dispatcher.
     */
    fun setDemoModeBlocking(on: Boolean): Boolean {
        AppSettings.setDemoMode(context, on)
        var createdSupervisor = false
        if (on && Supervisor.current(context) == null) {
            Supervisor.setUp(context, DEMO_SUPERVISOR, DEMO_PIN, demo = true)
            createdSupervisor = true
        }
        if (!on && Supervisor.isDemo(context)) Supervisor.remove(context)
        return createdSupervisor
    }

    /** Re-reads what [setDemoModeBlocking] changed; call on the main thread afterwards. */
    fun afterDemoModeChange() {
        demoMode = AppSettings.demoMode(context)
        supervisor = Supervisor.current(context)
    }

    fun onSupervisorChanged(info: SupervisorInfo?) {
        supervisor = info
    }

    fun selectWorker(workerHex: String) {
        Workers.setActive(context, workerHex)
        reloadWorkers()
    }

    fun addWorker(name: String, employer: String, district: String, site: String): Profile {
        val created = Workers.create(context, name, employer, district, site)
        reloadWorkers()
        return created
    }

    fun updateWorker(profile: Profile) {
        Workers.update(context, profile)
        reloadWorkers()
    }

    /** Onboarding keeps the id of a worker the first version already made, so their certificates stay theirs. */
    fun finishOnboarding(name: String, employer: String, district: String, site: String) {
        val existing = worker
        if (existing != null) {
            Workers.update(context, existing.copy(name = name, employer = employer, district = district, site = site))
        } else {
            Workers.create(context, name, employer, district, site)
        }
        AppSettings.setOnboarded(context)
        reloadWorkers()
        onboarded = true
    }

    private fun reloadWorkers() {
        workers = Workers.all(context)
        worker = Workers.active(context)
    }

    companion object {
        const val DEMO_PIN = "1234"
        const val DEMO_SUPERVISOR = "Demo Supervisor"
    }
}
