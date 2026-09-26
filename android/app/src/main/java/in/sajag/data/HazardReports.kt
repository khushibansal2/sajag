package `in`.sajag.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One hazard a worker reported from the field. Never edited after it is made. */
data class HazardReport(
    val id: String,
    val workerId: String,
    /** FIRE, GAS, ELECTRICAL, FALL, MACHINERY, SLIP or OTHER */
    val type: String,
    /** LOW, MEDIUM or HIGH */
    val severity: String,
    /** Free text: level, gallery, landmark. May be empty. */
    val location: String,
    val note: String,
    val createdAtMs: Long,
    val sent: Boolean = false,
    /** District id from geo/JharkhandMap.kt, or "" if the worker did not say. */
    val district: String = "",
)

/**
 * Hazard reports, kept on the phone until the sync worker delivers them.
 *
 * A short JSON list in SharedPreferences rather than a Room table: reports are
 * small, rare and never edited, and this keeps the attempt database (and its
 * exported schema) untouched.
 */
object HazardReports {
    private const val PREFS = "hazard_reports"
    private const val KEY = "reports"
    private const val MAX_KEPT = 200

    val TYPES = listOf("FIRE", "GAS", "ELECTRICAL", "FALL", "MACHINERY", "SLIP", "OTHER")
    val SEVERITIES = listOf("LOW", "MEDIUM", "HIGH")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun all(ctx: Context): List<HazardReport> {
        val raw = prefs(ctx).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i -> fromJson(array.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(ctx: Context, report: HazardReport) {
        save(ctx, (listOf(report) + all(ctx)).take(MAX_KEPT))
    }

    @Synchronized
    fun markSent(ctx: Context, ids: Set<String>) {
        save(ctx, all(ctx).map { if (it.id in ids) it.copy(sent = true) else it })
    }

    fun unsent(ctx: Context): List<HazardReport> = all(ctx).filter { !it.sent }

    fun toJson(r: HazardReport): JSONObject = JSONObject()
        .put("id", r.id)
        .put("workerId", r.workerId)
        .put("type", r.type)
        .put("severity", r.severity)
        .put("location", r.location)
        .put("note", r.note)
        .put("createdAtMs", r.createdAtMs)
        .put("sent", r.sent)
        .put("district", r.district)

    private fun fromJson(o: JSONObject) = HazardReport(
        id = o.getString("id"),
        workerId = o.optString("workerId"),
        type = o.getString("type"),
        severity = o.getString("severity"),
        location = o.optString("location"),
        note = o.optString("note"),
        createdAtMs = o.getLong("createdAtMs"),
        sent = o.optBoolean("sent", false),
        district = o.optString("district"),
    )

    private fun save(ctx: Context, list: List<HazardReport>) {
        val array = JSONArray()
        list.forEach { array.put(toJson(it)) }
        prefs(ctx).edit().putString(KEY, array.toString()).apply()
    }
}
