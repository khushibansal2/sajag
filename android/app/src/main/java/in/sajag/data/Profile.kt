package `in`.sajag.data

import android.content.Context
import `in`.sajag.i18n.Lang
import `in`.sajag.util.toHex
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * One worker who trains on this phone.
 *
 * [workerHex] is the 128-bit worker id written into every certificate; it never
 * changes once made. [district] is a district id from geo/JharkhandMap.kt
 * ("" if not given yet), and [site] is the mine, plant or training centre.
 */
data class Profile(
    val name: String,
    val employer: String,
    val workerHex: String,
    val district: String = "",
    val site: String = "",
) {
    val enrolled: Boolean get() = name.isNotBlank() && employer.isNotBlank()

    /** The short form printed on the ID card and read out by a supervisor. */
    val shortId: String get() = workerHex.take(8).uppercase()
}

object Prefs {
    internal fun prefs(ctx: Context) = ctx.getSharedPreferences("sajag", Context.MODE_PRIVATE)

    fun lang(ctx: Context): Lang =
        runCatching { Lang.valueOf(prefs(ctx).getString("lang", Lang.HI.name)!!) }.getOrDefault(Lang.HI)

    fun setLang(ctx: Context, lang: Lang) = prefs(ctx).edit().putString("lang", lang.name).apply()
}

/**
 * The workers who use this phone. At a training centre one phone is shared by
 * a whole batch, so the phone keeps a small directory and one active worker.
 *
 * Kept as a JSON list in SharedPreferences. The first version of the app kept
 * a single worker under "name", "employer" and "worker_id"; that worker is
 * carried over with the same id, so certificates already on the phone still
 * belong to them.
 */
object Workers {
    private const val KEY_LIST = "workers"
    private const val KEY_ACTIVE = "active_worker"
    private val random = SecureRandom()

    @Synchronized
    fun all(ctx: Context): List<Profile> {
        val p = Prefs.prefs(ctx)
        val raw = p.getString(KEY_LIST, null)
        if (raw == null) {
            // Carry over the single worker the first version stored, if any.
            val legacyId = p.getString("worker_id", null) ?: return emptyList()
            val legacy = Profile(p.getString("name", "") ?: "", p.getString("employer", "") ?: "", legacyId)
            save(ctx, listOf(legacy))
            p.edit().putString(KEY_ACTIVE, legacyId).apply()
            return listOf(legacy)
        }
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i -> fromJson(array.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    /** The worker who is training now, or null before anyone has been added. */
    fun active(ctx: Context): Profile? {
        val list = all(ctx)
        val id = Prefs.prefs(ctx).getString(KEY_ACTIVE, null)
        return list.firstOrNull { it.workerHex == id } ?: list.firstOrNull()
    }

    fun setActive(ctx: Context, workerHex: String) {
        Prefs.prefs(ctx).edit().putString(KEY_ACTIVE, workerHex).apply()
    }

    /** Adds a new worker with a fresh id and makes them the active one. */
    @Synchronized
    fun create(ctx: Context, name: String, employer: String, district: String, site: String): Profile {
        val profile = Profile(
            name = cleanName(name),
            employer = cleanEmployer(employer),
            workerHex = ByteArray(16).also { random.nextBytes(it) }.toHex(),
            district = district,
            site = site.trim(),
        )
        save(ctx, all(ctx) + profile)
        setActive(ctx, profile.workerHex)
        return profile
    }

    /** Updates the details of an existing worker. The id never changes. */
    @Synchronized
    fun update(ctx: Context, profile: Profile) {
        val cleaned = profile.copy(
            name = cleanName(profile.name),
            employer = cleanEmployer(profile.employer),
            site = profile.site.trim(),
        )
        save(ctx, all(ctx).map { if (it.workerHex == cleaned.workerHex) cleaned else it })
    }

    fun cleanName(name: String) = name.trim().replace(Regex("\\s+"), " ")

    /** Employer codes are upper case and at most 12 bytes, the certificate's budget. */
    fun cleanEmployer(employer: String) = employer.trim().uppercase().take(12)

    private fun save(ctx: Context, list: List<Profile>) {
        val array = JSONArray()
        list.forEach { array.put(toJson(it)) }
        Prefs.prefs(ctx).edit().putString(KEY_LIST, array.toString()).apply()
    }

    private fun toJson(p: Profile) = JSONObject()
        .put("id", p.workerHex)
        .put("name", p.name)
        .put("employer", p.employer)
        .put("district", p.district)
        .put("site", p.site)

    private fun fromJson(o: JSONObject) = Profile(
        name = o.optString("name"),
        employer = o.optString("employer"),
        workerHex = o.getString("id"),
        district = o.optString("district"),
        site = o.optString("site"),
    )
}

/**
 * Phone-wide settings. The control room number and the assembly point are set
 * by the supervisor, because a wrong emergency number is worse than none.
 */
object AppSettings {
    private fun p(ctx: Context) = Prefs.prefs(ctx)

    fun onboarded(ctx: Context): Boolean = p(ctx).getBoolean("onboarded", false)
    fun setOnboarded(ctx: Context) = p(ctx).edit().putBoolean("onboarded", true).apply()

    fun controlRoom(ctx: Context): String = p(ctx).getString("control_room", "") ?: ""
    fun setControlRoom(ctx: Context, number: String) =
        p(ctx).edit().putString("control_room", cleanPhone(number)).apply()

    fun assemblyPoint(ctx: Context): String = p(ctx).getString("assembly_point", "") ?: ""
    fun setAssemblyPoint(ctx: Context, text: String) =
        p(ctx).edit().putString("assembly_point", text.trim().take(80)).apply()

    fun demoMode(ctx: Context): Boolean = p(ctx).getBoolean("demo_mode", false)
    fun setDemoMode(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("demo_mode", on).apply()

    /** World-anchored AR when the phone supports it. Off means the plain camera view. */
    fun useAr(ctx: Context): Boolean = p(ctx).getBoolean("use_ar", true)
    fun setUseAr(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("use_ar", on).apply()

    /** Digits, and a leading + or internal spaces and dashes; anything else is dropped. */
    fun cleanPhone(number: String): String =
        number.trim().filterIndexed { i, c -> c.isDigit() || (c == '+' && i == 0) || c == ' ' || c == '-' }.take(20).trim()
}

/** ULID: 48-bit millisecond time + 80 random bits, Crockford base32. Sortable, collision-free offline. */
object Ulid {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val random = SecureRandom()

    fun next(now: Long = System.currentTimeMillis()): String {
        val sb = StringBuilder(26)
        for (i in 9 downTo 0) sb.append(ALPHABET[((now ushr (i * 5)) and 31).toInt()])
        repeat(16) { sb.append(ALPHABET[random.nextInt(32)]) }
        return sb.toString()
    }
}
