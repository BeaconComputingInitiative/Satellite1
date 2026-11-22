// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
package org.beaconcomputing.satellite1

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Encrypted event payloads, ordered JSONL export and acknowledgment-based upload state. */
class EvidenceStore(context: Context) : SQLiteOpenHelper(context, "satellite1-events.db", null, 3) {
    private val crypto = CryptoBox()
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE events(id TEXT PRIMARY KEY, created_utc TEXT NOT NULL, kind TEXT NOT NULL, ciphertext TEXT NOT NULL, uploaded INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX idx_pending ON events(uploaded,created_utc)")
        db.execSQL("CREATE TABLE cell_state (id INTEGER PRIMARY KEY CHECK (id=1), ciphertext TEXT NOT NULL)")
        createV3Tables(db)
    }
    private fun createV3Tables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE cell_state_by_sub (subscription_id INTEGER PRIMARY KEY, observed_utc TEXT NOT NULL, ciphertext TEXT NOT NULL)")
        db.execSQL("CREATE TABLE default_data_subscription (id INTEGER PRIMARY KEY CHECK (id=1), subscription_id INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (newVersion != 3 || oldVersion !in 1..2) {
            throw IllegalStateException("Unsupported database migration: preserve existing evidence")
        }
        if (oldVersion == 1) {
            db.execSQL("CREATE TABLE cell_state (id INTEGER PRIMARY KEY CHECK (id=1), ciphertext TEXT NOT NULL)")
        }
        // Preserve v2's mixed-subscription cell_state as historical evidence, unmodified.
        // Do not automatically promote it to per-SIM baseline: its provenance is ambiguous.
        createV3Tables(db)
    }
    /** Uses the current transaction when called from trackCellSample(). */
    private fun insertEvent(db: SQLiteDatabase, kind: String, facts: JSONObject): String {
        require(kind.matches(Regex("[A-Z0-9_]{3,48}")))
        val id = UUID.randomUUID().toString()
        val timestamp = Instant.now().toString()
        val record = JSONObject()
            .put("initiative", "BEACON COMPUTING INITIATIVE")
            .put("developer_seal", "CJB-DEV-2026")
            .put("schema", "satellite1.telemetry.v1-candidate")
            .put("event_id", id)
            .put("timestamp_utc", timestamp)
            .put("kind", kind)
            .put("facts", facts)
        val base = record.toString()
        record.put("record_sha256", hex(sha256(base.toByteArray(Charsets.UTF_8))))
        val values = android.content.ContentValues().apply {
            put("id", id); put("created_utc", timestamp)
            put("kind", kind); put("ciphertext", crypto.seal(record.toString()))
        }
        db.insertOrThrow("events", null, values)
        return id
    }
    fun append(kind: String, facts: JSONObject): String = insertEvent(writableDatabase, kind, facts)

    /** Atomic, per-SIM baseline update. Never reinterpret mixed legacy baselines as a handoff. */
    fun trackCellSample(snapshot: JSONObject, source: String, location: JSONObject?): String? {
        val state = CellChangeTracker.derive(snapshot, source) ?: return null
        if (location != null) state.put("phone_location_at_observation", location)
        val subId = state.getInt("subscription_id")
        val db = writableDatabase
        db.beginTransaction()
        try {
            val previous = db.rawQuery(
                "SELECT ciphertext FROM cell_state_by_sub WHERE subscription_id=?", arrayOf(subId.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) JSONObject(crypto.unseal(cursor.getString(0))) else null
            }
            val kind = CellChangeTracker.classify(previous, state)
            if (kind != null) insertEvent(db, kind, CellChangeTracker.details(kind, previous, state, location))
            // The classifier only evaluates an unambiguous per-SIM single-cell replacement.
            if (kind == "CELL_SERVING_CHANGED") {
                val risk = RogueCellClassifier.evaluate(previous, state)
                insertEvent(db, "CELL_RISK_ASSESSMENT", risk)
            }
            val row = android.content.ContentValues().apply {
                put("subscription_id", subId)
                put("observed_utc", state.getString("observed_utc"))
                put("ciphertext", crypto.seal(state.toString()))
            }
            check(db.insertWithOnConflict("cell_state_by_sub", null, row,
                SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "Cannot preserve per-SIM baseline" }
            db.setTransactionSuccessful()
            return kind
        } finally { db.endTransaction() }
    }

    /** Report default-data subscription changes separately from physical-cell identity events. */
    fun trackDefaultSubscription(subId: Int): Boolean {
        if (subId < 0) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            val prior = db.rawQuery(
                "SELECT subscription_id FROM default_data_subscription WHERE id=1", null
            ).use { cur -> if (cur.moveToFirst()) cur.getInt(0) else null }
            val changed = CellChangeRules.subscriptionChanged(prior, subId)
            if (changed) insertEvent(db, "CELL_SUBSCRIPTION_CHANGED", JSONObject()
                .put("previous_default_subscription_id", prior)
                .put("current_default_subscription_id", subId)
                .put("physical_tower_change_verified", false))
            val row = android.content.ContentValues().apply { put("id", 1); put("subscription_id", subId) }
            check(db.insertWithOnConflict("default_data_subscription", null, row,
                SQLiteDatabase.CONFLICT_REPLACE) != -1L)
            db.setTransactionSuccessful()
            return changed
        } finally { db.endTransaction() }
    }
    fun latestCellState(): JSONObject? = readableDatabase.rawQuery(
        "SELECT ciphertext FROM cell_state_by_sub ORDER BY observed_utc DESC LIMIT 1", null
    ).use { cursor ->
        if (cursor.moveToFirst()) JSONObject(crypto.unseal(cursor.getString(0))) else null
    }
    fun recentCellTransitions(limit: Int = 8): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        readableDatabase.query("events", arrayOf("ciphertext"),
            "kind IN (?,?,?,?)", arrayOf("CELL_SERVING_CHANGED", "CELL_REGISTERED_SET_CHANGED",
                "CELL_SUBSCRIPTION_CHANGED", "CELL_BASELINE_ESTABLISHED"),
            null, null, "created_utc DESC,id DESC", limit.coerceIn(1, 30).toString()).use { cursor ->
            while (cursor.moveToNext()) out.add(JSONObject(crypto.unseal(cursor.getString(0))))
        }
        return out
    }
    fun transitionCount(): Long = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM events WHERE kind='CELL_SERVING_CHANGED'", null
    ).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }
    fun recentRiskAssessments(limit: Int = 5): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        readableDatabase.query("events", arrayOf("ciphertext"), "kind=?",
            arrayOf("CELL_RISK_ASSESSMENT"), null, null, "created_utc DESC,id DESC",
            limit.coerceIn(1, 20).toString()).use { c ->
            while (c.moveToNext()) out.add(JSONObject(crypto.unseal(c.getString(0))))
        }
        return out
    }
    fun setChangeCount(): Long = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM events WHERE kind='CELL_REGISTERED_SET_CHANGED'", null
    ).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }

    data class Entry(val id: String, val jsonl: String)
    fun pending(limit: Int = 100): List<Entry> {
        val entries = mutableListOf<Entry>()
        readableDatabase.query("events", arrayOf("id", "ciphertext"), "uploaded=0", null,
            null, null, "created_utc ASC, id ASC", limit.coerceIn(1, 100).toString()).use { cursor ->
            while (cursor.moveToNext()) entries.add(Entry(cursor.getString(0), crypto.unseal(cursor.getString(1))))
        }
        return entries
    }
    fun acknowledge(ids: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) {
                val values = android.content.ContentValues().apply { put("uploaded", 1) }
                db.update("events", values, "id=? AND uploaded=0", arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun count(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM events", null).use {
        it.moveToFirst(); it.getLong(0)
    }
    data class ExportReceipt(val records: Int, val payloadSha256: String, val fileSha256: String)
    fun exportJsonl(resolver: ContentResolver, target: Uri): ExportReceipt {
        val payloadDigest = MessageDigest.getInstance("SHA-256")
        val fileDigest = MessageDigest.getInstance("SHA-256")
        var count = 0
        require(target.scheme == "content") { "Storage Access Framework URI required" }
        val stream = resolver.openOutputStream(target, "wt") ?: error("Export destination unavailable")
        stream.use { output ->
            readableDatabase.query("events", arrayOf("ciphertext"), null, null,
                null, null, "created_utc ASC,id ASC").use { cursor ->
                while (cursor.moveToNext()) {
                    val line = crypto.unseal(cursor.getString(0)) + "\n"
                    val bytes = line.toByteArray(Charsets.UTF_8)
                    payloadDigest.update(bytes)
                    fileDigest.update(bytes)
                    output.write(bytes)
                    count++
                }
            }
            val digest = hex(payloadDigest.digest())
            val trailer = JSONObject()
                .put("initiative", "BEACON COMPUTING INITIATIVE")
                .put("developer_seal", "CJB-DEV-2026")
                .put("kind", "EXPORT_MANIFEST")
                .put("schema", "satellite1.jsonl-export.v1")
                .put("events", count)
                .put("payload_sha256", digest)
                .toString() + "\n"
            val footer = trailer.toByteArray(Charsets.UTF_8)
            output.write(footer)
            fileDigest.update(footer)
            output.flush()
            return ExportReceipt(count, digest, hex(fileDigest.digest()))
        }
    }
    companion object {
        fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
