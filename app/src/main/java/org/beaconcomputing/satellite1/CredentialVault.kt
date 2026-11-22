// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
package org.beaconcomputing.satellite1

import android.content.Context

/** The bearer credential is never placed into JSONL, logs, or normal SharedPreferences. */
class CredentialVault(context: Context) {
    private val p = context.getSharedPreferences("satellite1-secure", Context.MODE_PRIVATE)
    private val crypto = CryptoBox()
    fun save(token: String) {
        if (token.isBlank()) p.edit().remove("token").apply()
        else p.edit().putString("token", crypto.seal(token.trim())).apply()
    }
    fun read(): String = p.getString("token", null)?.let { crypto.unseal(it) } ?: ""
}
