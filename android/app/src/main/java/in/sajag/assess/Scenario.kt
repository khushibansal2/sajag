package `in`.sajag.assess

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RubricItem(
    val id: String,
    val kind: String,
    val competency: String,
    val weight: Double,
    val params: Map<String, Any?>,
)

data class Beat(val id: String, val title: String, val teaches: String, val rubric: List<RubricItem>)

/**
 * The scenario file, exactly as core/scenarios ships it. The APK bundles those
 * files unchanged (see sourceSets in build.gradle.kts), so the phone and the
 * server always score against the same rubric.
 */
data class Scenario(
    val id: String,
    val tierCalibration: Map<Int, Double>,
    val hardFails: Set<String>,
    val beats: List<Beat>,
) {
    companion object {
        fun load(context: Context, asset: String): Scenario =
            parse(context.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() })

        fun parse(json: String): Scenario {
            val o = JSONObject(json)
            val cal = o.optJSONObject("tier_calibration")
            return Scenario(
                id = o.getString("id"),
                tierCalibration = cal?.keys()?.asSequence()
                    ?.associate { it.toInt() to cal.getDouble(it) } ?: emptyMap(),
                hardFails = o.optJSONArray("hard_fails")?.strings()?.toSet() ?: emptySet(),
                beats = o.getJSONArray("beats").objects().map { b ->
                    Beat(
                        id = b.getString("id"),
                        title = b.getString("title"),
                        teaches = b.optString("teaches"),
                        rubric = b.optJSONArray("rubric")?.objects()?.map { r ->
                            RubricItem(
                                id = r.getString("id"),
                                kind = r.getString("kind"),
                                competency = r.getString("competency"),
                                weight = r.optDouble("weight", 1.0),
                                params = toMap(r.optJSONObject("params") ?: JSONObject()),
                            )
                        } ?: emptyList(),
                    )
                },
            )
        }

        private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
        private fun JSONArray.strings() = (0 until length()).map { getString(it) }

        private fun toMap(o: JSONObject): Map<String, Any?> =
            o.keys().asSequence().associateWith { k -> unwrap(o.get(k)) }

        private fun unwrap(v: Any?): Any? = when {
            v == null || v == JSONObject.NULL -> null
            v is JSONObject -> toMap(v)
            v is JSONArray -> (0 until v.length()).map { unwrap(v.get(it)) }
            else -> v
        }
    }
}
