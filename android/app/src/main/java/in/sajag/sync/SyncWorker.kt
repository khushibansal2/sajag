package `in`.sajag.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import `in`.sajag.BuildConfig
import `in`.sajag.SajagApp
import `in`.sajag.credential.DeviceIdentity
import `in`.sajag.data.HazardReports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pushes sealed attempt bundles, and any hazard reports, to the issuer whenever
 * a network appears.
 *
 * Safe to run any number of times: events are append-only and ULID-keyed, and
 * the server drops duplicates, so a push that dies halfway through a 2G tail
 * is simply retried. WorkManager keeps the job across reboots.
 *
 * Each attempt goes as one signed bundle (see [SyncBundle]) with a fresh
 * `issuedAt`, so the server can refuse altered, foreign, stale and replayed
 * bundles.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = (applicationContext as SajagApp).db.attempts()
        val key = DeviceIdentity.deviceKey(applicationContext)

        val pending = dao.unsynced()
        for ((attemptId, events) in pending.groupBy { it.attemptId }) {
            val attempt = dao.attempt(attemptId) ?: continue
            val body = SyncBundle.signed(attempt, events, key)
            val ok = withContext(Dispatchers.IO) {
                runCatching { post("${BuildConfig.SERVER_URL}/v1/sync/bundle", body) }.getOrDefault(false)
            }
            if (!ok) return Result.retry()
            dao.markSynced(events.map { it.eventId })
        }

        val reports = HazardReports.unsent(applicationContext)
        if (reports.isNotEmpty()) {
            val array = JSONArray()
            reports.forEach { array.put(HazardReports.toJson(it)) }
            val body = JSONObject()
                .put("deviceKeyId", DeviceIdentity.deviceKeyId(applicationContext))
                .put("reports", array)
                .toString()
            val ok = withContext(Dispatchers.IO) {
                runCatching { post("${BuildConfig.SERVER_URL}/v1/hazards", body) }.getOrDefault(false)
            }
            if (!ok) return Result.retry()
            HazardReports.markSent(applicationContext, reports.map { it.id }.toSet())
        }
        return Result.success()
    }

    private fun post(url: String, json: String): Boolean {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("sajag-sync", ExistingWorkPolicy.KEEP, request)
        }
    }
}
