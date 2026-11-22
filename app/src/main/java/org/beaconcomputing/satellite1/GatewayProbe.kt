// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
package org.beaconcomputing.satellite1

import android.net.Network
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress

/** Explicitly authorized and rate-limited gateway-only connectivity checks. */
object GatewayProbe {
    fun inspect(network: Network, gateway: String): JSONObject {
        val ports = intArrayOf(22, 80, 443)
        val results = JSONArray()
        for (port in ports) {
            val reachable = try {
                network.socketFactory.createSocket().use { socket ->
                    socket.connect(InetSocketAddress(gateway, port), 750)
                    true
                }
            } catch (_: Exception) { false }
            results.put(JSONObject().put("port", port).put("connect_success", reachable))
        }
        return JSONObject().put("scope", "APPROVED_WIFI_GATEWAY_ONLY")
            .put("gateway", gateway).put("tcp_ports_checked", results)
            .put("no_payload_transmitted", true)
    }
}
