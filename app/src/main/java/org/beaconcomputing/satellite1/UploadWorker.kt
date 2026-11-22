// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
package org.beaconcomputing.satellite1

import android.content.Context
import androidx.work.*
import org.json.JSONObject
import javax.net.ssl.HttpsURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit
import android.util.Log

/** Candidate protocol: server MUST acknowledge SHA-256 and count; never delete unacknowledged data. */
class UploadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val policy = Policy(applicationContext)
        if (!policy.autoUpload || policy.endpoint.isBlank()) return Result.success()
        val uri = try { URI(policy.endpoint) } catch (_: Exception) { return Result.failure() }
        if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() ||
            uri.userInfo != null || uri.fragment != null) return Result.failure()
        val token = try { CredentialVault(applicationContext).read() } catch (_: Exception) { return Result.failure() }
        if (token.isBlank()) return Result.success() // Fail closed until enrolled.
        val store = EvidenceStore(applicationContext)
        try {
            val pending = store.pending(100)
            if (pending.isEmpty()) return Result.success()
            val payload = pending.joinToString(separator = "\n", postfix = "\n") { it.jsonl }
            val digest = EvidenceStore.hex(EvidenceStore.sha256(payload.toByteArray(Charsets.UTF_8)))
            val conn = URL(uri.toString()).openConnection() as? HttpsURLConnection ?: return Result.failure()
            try {
                conn.requestMethod = "POST"
                conn.instanceFollowRedirects = false // no transparent endpoint downgrade/redirect
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-ndjson; charset=utf-8")
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("X-BCI-Developer-Seal", "CJB-DEV-2026")
                conn.setRequestProperty("X-BCI-Batch-SHA256", digest)
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode !in 200..299) return Result.retry()
                val reply = conn.inputStream.bufferedReader().use { it.readText() }
                val ack = JSONObject(reply)
                if (ack.optString("batch_sha256") != digest ||
                    ack.optInt("accepted_count", -1) != pending.size ||
                    ack.optString("receipt_id").isBlank()) return Result.failure()
                store.acknowledge(pending.map { it.id })
                Log.i("SATELLITE1", "ORBIT11_RECEIPT_VERIFIED: batch count=${pending.size}")
                return Result.success()
            } finally { conn.disconnect() }
        } catch (_: Exception) { return Result.retry() }
        finally { store.close() }
    }
    companion object {
        fun oneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true).build()).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "satellite1-user-initiated-upload", ExistingWorkPolicy.KEEP, request)
        }
        fun schedule(context: Context) {
            val work = PeriodicWorkRequestBuilder<UploadWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "satellite1-verified-upload", ExistingPeriodicWorkPolicy.UPDATE, work)
        }
    }
}
