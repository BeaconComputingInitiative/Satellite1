// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
package org.beaconcomputing.satellite1

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import android.provider.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import java.time.Instant
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.concurrent.thread

internal object Bci {
    val Black = Color(0xFF05080D)
    val Maroon = Color(0xFF650D28)
    val Cyan = Color(0xFF00E5FF)
    val Graphite = Color(0xFF111820)
    val White = Color(0xFFF4F8FB)
    val Muted = Color(0xFFA8BAC6)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        UploadWorker.schedule(this)
        setContent {
            var bootComplete by rememberSaveable { mutableStateOf(false) }
            val animationScale = remember {
                Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            }
            LaunchedEffect(bootComplete) {
                if (!bootComplete) {
                    if (animationScale > 0f) delay(2600L)
                    bootComplete = true
                }
            }
            if (bootComplete) SatelliteScreen(this)
            else SatelliteBootScreen(onSkip = { bootComplete = true })
        }
    }
}

@Composable
private fun SatelliteScreen(activity: Activity) {
    val policy = remember { Policy(activity) }
    // The saved preference is user intent, NOT proof that the OS is running MonitorService.
    var monitoring by remember { mutableStateOf(MonitorRuntimeState.active) }
    var location by remember { mutableStateOf(policy.locationEnabled) }
    var cellular by remember { mutableStateOf(policy.cellularEnabled) }
    var activeProbes by remember { mutableStateOf(policy.probeApproved) }
    var upload by remember { mutableStateOf(policy.autoUpload) }
    var url by remember { mutableStateOf(policy.endpoint) }
    var credential by remember { mutableStateOf("") }
    var approved by remember { mutableStateOf(policy.approvedSsid) }
    var message by remember { mutableStateOf("Ready • Evidence remains on device until delivery is configured") }
    var eventCount by remember { mutableStateOf(0L) }
    var approvalDialog by remember { mutableStateOf(false) }
    var probeDialog by remember { mutableStateOf(false) }
    var exportedSha by remember { mutableStateOf("") }
    var currentCellState by remember { mutableStateOf<JSONObject?>(null) }
    var cellTransitions by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var riskAssessments by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var cellChangeCount by remember { mutableStateOf(0L) }
    var cellSetChangeCount by remember { mutableStateOf(0L) }

    fun refresh() {
        EvidenceStore(activity).use {
            eventCount = it.count()
            monitoring = MonitorRuntimeState.active
            currentCellState = it.latestCellState()
            cellTransitions = it.recentCellTransitions(8)
            riskAssessments = it.recentRiskAssessments(5)
            cellChangeCount = it.transitionCount()
            cellSetChangeCount = it.setChangeCount()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(10_000L)
        }
    }
    // Reflect actual foreground-service state (also after Android stops the service).
    LaunchedEffect(Unit) {
        while (true) {
            monitoring = MonitorRuntimeState.active
            delay(1_000L)
        }
    }

    val startMonitor = {
        policy.monitoringEnabled = true
        try {
            ContextCompat.startForegroundService(activity, Intent(activity, MonitorService::class.java))
            message = "Monitoring requested • checking actual foreground-service state"
        } catch (err: Exception) {
            policy.monitoringEnabled = false
            monitoring = false
            message = "HOLD: Foreground service start denied (${err.javaClass.simpleName})"
        }
    }
    val permissionRequester = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startMonitor()
        else message = "HOLD: Precise location required for approved Wi-Fi identity and cell collection"
    }
    val jsonlExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-ndjson")
    ) { uri ->
        if (uri != null) {
            message = "Export in progress"
            thread(name = "bci-jsonl-export") {
                try {
                    val receipt = EvidenceStore(activity).use { it.exportJsonl(activity.contentResolver, uri) }
                    activity.runOnUiThread {
                        exportedSha = receipt.fileSha256
                        message = "Exported ${receipt.records} events • whole-file SHA-256 below"
                        refresh()
                    }
                } catch (error: Exception) {
                    activity.runOnUiThread { message = "Export failed: ${error.javaClass.simpleName}" }
                }
            }
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Bci.Cyan, secondary = Bci.Maroon, background = Bci.Black,
        surface = Bci.Graphite, onPrimary = Bci.Black, onBackground = Bci.White,
        onSurface = Bci.White
    )) {
        Column(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Bci.Black, Bci.Graphite, Bci.Black)))
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            MissionControlHeader(monitoring = monitoring, eventCount = eventCount,
                servingChanges = cellChangeCount)
            CardPanel("MISSION CONTROL") {
                Text("Continuous visible monitoring • 30-minute reconciliation", color = Bci.Muted)
                Text("Network reconnaissance is limited to approved Wi-Fi. Cellular and GPS collection are separately controlled.",
                    fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        if (monitoring) {
                            policy.monitoringEnabled = false
                            monitoring = false
                            activity.startService(Intent(activity, MonitorService::class.java)
                                .setAction(MonitorService.ACTION_STOP))
                            message = "Monitoring stop requested"
                        } else {
                            val permissions = mutableListOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (cellular) permissions.add(Manifest.permission.READ_PHONE_STATE)
                            if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                            if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                                (!cellular || activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)) {
                                startMonitor()
                            } else permissionRequester.launch(permissions.toTypedArray())
                        }
                    }) { Text(if (monitoring) "STOP MONITORING" else "START MONITORING") }
                    OutlinedButton(onClick = { refresh() }) { Text("REFRESH") }
                }
                Text("LIVE SERVICE: ${if (monitoring) "ACTIVE" else "STOPPED"} • ${MonitorRuntimeState.state}",
                    color = if (monitoring) Bci.Cyan else Bci.Muted, fontSize = 12.sp)
                Text(message, color = Bci.Cyan, fontSize = 12.sp)
            }
            CardPanel("APPROVED WI-FI  //  RECON") {
                Text(if (approved.isBlank()) "No Wi-Fi authorized" else "Authorized: $approved / ${policy.approvedBssid}",
                    color = Bci.Muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { approvalDialog = true }) { Text("AUTHORIZE WI-FI") }
                    OutlinedButton(onClick = {
                        policy.revoke(); approved = ""; activeProbes = false
                        message = "Wi-Fi authorization revoked"
                    }) { Text("REVOKE") }
                }
                SettingSwitch("Gateway-only active probes", activeProbes, onChange = { wanted ->
                    if (wanted) probeDialog = true else {
                        activeProbes = false; policy.probeApproved = false
                    }
                })
                Text("Rate-limited checks: gateway TCP 22, 80, 443 only; no subnet sweeps.",
                    fontSize = 12.sp, color = Bci.Muted)
            }
            CardPanel("RADIO + GEOLOCATION") {
                SettingSwitch("GPS/network location fixes", location, onChange = {
                    location = it; policy.locationEnabled = it
                })
                SettingSwitch("Serving + neighboring cellular measurements", cellular, onChange = {
                    cellular = it; policy.cellularEnabled = it
                })
                Text("Tower identifiers are phone-reported measurements, not verified tower coordinates. No IMSI, IMEI, SMS or call contents.",
                    fontSize = 12.sp, color = Bci.Muted)
            }
            CardPanel("CELLULAR INTELLIGENCE  //  LIVE") {
                Text("Single-cell identity replacements: $cellChangeCount", color = Bci.Cyan,
                    fontWeight = FontWeight.Bold)
                Text("Registered-cell set changes: $cellSetChangeCount", color = Bci.Muted, fontSize = 12.sp)
                val latest = currentCellState
                val serving = latest?.optJSONArray("registered_cells")
                Text("Last observation (SIM ${latest?.optInt("subscription_id", -1) ?: -1}): ${latest?.optString("observed_utc") ?: "Not yet available"}",
                    color = Bci.Muted, fontSize = 11.sp)
                if (serving == null || serving.length() == 0) {
                    Text("No identified serving cell recorded. Enable cellular monitoring and grant precise location + phone permission.",
                        color = Bci.Muted, fontSize = 12.sp)
                } else {
                    for (index in 0 until minOf(serving.length(), 4)) {
                        val cell = serving.optJSONObject(index) ?: continue
                        Text(cellDescription(cell), color = Bci.White,
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text(cellSignalDescription(cell), color = Bci.Cyan,
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
                HorizontalDivider(color = Bci.Maroon)
                Text("SERVING CELL CHANGE HISTORY", color = Bci.Cyan,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (cellTransitions.isEmpty()) {
                    Text("No baseline or cell-change events recorded yet", fontSize = 12.sp, color = Bci.Muted)
                } else {
                    for (event in cellTransitions) {
                        val kind = event.optString("kind")
                        val facts = event.optJSONObject("facts") ?: JSONObject()
                        Text("${event.optString("timestamp_utc")}  •  $kind",
                            color = Bci.Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text("${servingSummary(facts.optJSONArray("previous"))}  →  ${servingSummary(facts.optJSONArray("current"))}",
                            color = Bci.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { refresh() }) { Text("REFRESH CELL EVENTS") }
                }
                HorizontalDivider(color = Bci.Maroon)
                Text("ROGUE-CELL ANOMALY SCREENING  //  UNCONFIRMED", color = Bci.Cyan,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (riskAssessments.isEmpty()) {
                    Text("No assessed single-cell replacements yet. This is not proof of a safe or unsafe network.",
                        color = Bci.Muted, fontSize = 11.sp)
                } else {
                    for (record in riskAssessments) {
                        val facts = record.optJSONObject("facts") ?: JSONObject()
                        Text("${record.optString("timestamp_utc")}  •  ${facts.optString("classification", "INSUFFICIENT_EVIDENCE")}",
                            color = Bci.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        val reasons = facts.optJSONArray("reason_codes")
                        Text((0 until (reasons?.length() ?: 0)).joinToString(" • ") { reasons?.optString(it) ?: "" },
                            color = Bci.Muted, fontSize = 10.sp)
                    }
                }
                Text("Suspected does NOT mean a fake tower is verified. No attacker identity, location, or interception can be inferred from these readings.",
                    color = Bci.Muted, fontSize = 11.sp)
                Text("Per-SIM baselines; stale readings excluded from transitions. A cell change does not prove a different tower. Included in JSONL export.",
                    color = Bci.Muted, fontSize = 11.sp)
            }
            CardPanel("ORBIT11 DELIVERY") {
                SettingSwitch("Automatic verified uploads every ~30 minutes", upload, onChange = {
                    upload = it; policy.autoUpload = it
                })
                OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true,
                    label = { Text("HTTPS API endpoint") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = credential, onValueChange = { credential = it }, singleLine = true,
                    label = { Text("New enrollment token (hidden)") },
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val valid = url.isBlank() || (url.startsWith("https://") &&
                        try { java.net.URI(url).host != null } catch (_: Exception) { false })
                    if (!valid) message = "HOLD: HTTPS endpoint required"
                    else {
                        policy.endpoint = url
                        if (credential.isNotBlank()) { CredentialVault(activity).save(credential); credential = "" }
                        UploadWorker.schedule(activity)
                        message = "Settings saved. Upload requires enrolled API and matching receipt contract."
                    }
                }) { Text("SAVE DELIVERY CONFIGURATION") }
                OutlinedButton(onClick = {
                    if (!policy.autoUpload || policy.endpoint.isBlank() ||
                        CredentialVault(activity).read().isBlank()) {
                        message = "HOLD: Enable uploads, save HTTPS endpoint and enroll device first"
                    } else {
                        UploadWorker.oneShot(activity)
                        message = "One-time authenticated test upload queued; review server receipt and local logs"
                    }
                }) { Text("TEST ONE AUTHENTICATED UPLOAD") }
                Text("No endpoint is preconfigured. Delivery requires TLS, enrollment and a server receipt matching batch SHA-256 and record count.",
                    color = Bci.Muted, fontSize = 12.sp)
            }
            CardPanel("EVIDENCE VAULT  //  JSONL") {
                Text("Local encrypted events: $eventCount", color = Bci.White, fontWeight = FontWeight.Bold)
                Button(onClick = {
                    jsonlExporter.launch("SATELLITE1-${Instant.now().toString().take(10)}.jsonl")
                }) { Text("EXPORT JSONL TO PHONE") }
                Text("Export uses Android's file picker. The JSONL ends with an EXPORT_MANIFEST containing the preceding event-lines SHA-256.",
                    fontSize = 12.sp, color = Bci.Muted)
                if (exportedSha.isNotBlank()) {
                    Text("COMPLETE FILE SHA-256", color = Bci.Cyan, fontSize = 11.sp)
                    Text(exportedSha, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        color = Bci.White)
                    OutlinedButton(onClick = {
                        val manager = activity.getSystemService(android.content.ClipboardManager::class.java)
                        manager.setPrimaryClip(android.content.ClipData.newPlainText("JSONL SHA-256", exportedSha))
                    }) { Text("COPY SHA-256") }
                }
            }
            Text("OBSERVE ACCURATELY  •  PRESERVE FAITHFULLY  •  ANALYZE TRANSPARENTLY",
                fontSize = 10.sp, color = Bci.Muted, letterSpacing = .5.sp)
        }
        if (approvalDialog) {
            val wifi = WifiTelemetry(activity).currentWifiForApproval()
            AlertDialog(onDismissRequest = { approvalDialog = false },
                title = { Text("Authorize this Wi-Fi network?") },
                text = { Text("Confirm you administer or have permission to assess this Wi-Fi. Only the current SSID + BSSID will be allowed. Network names can be spoofed; this is a scope filter, not proof of ownership.\n\nCurrent: ${wifi?.ssid ?: "Unavailable"} / ${wifi?.bssid ?: "Unavailable"}") },
                confirmButton = { Button(onClick = {
                    approvalDialog = false
                    if (wifi != null && policy.approve(wifi)) {
                        approved = policy.approvedSsid
                        activeProbes = false
                        message = "Approved Wi-Fi identity registered"
                    } else message = "HOLD: Wi-Fi identity unavailable; grant precise location and enable Location"
                }) { Text("I AUTHORIZE") } },
                dismissButton = { TextButton(onClick = { approvalDialog = false }) { Text("CANCEL") } })
        }
        if (probeDialog) AlertDialog(onDismissRequest = { probeDialog = false },
            title = { Text("Authorize active gateway checks?") },
            text = { Text("Only on the approved Wi-Fi: the device will attempt TCP connections to its gateway on ports 22, 80, and 443, at most once each 30 minutes. Confirm network-owner permission.") },
            confirmButton = { Button(onClick = {
                probeDialog = false
                if (policy.approvedSsid.isNotBlank()) {
                    policy.probeApproved = true; activeProbes = true
                } else message = "HOLD: Approve Wi-Fi first"
            }) { Text("AUTHORIZE") } },
            dismissButton = { TextButton(onClick = { probeDialog = false }) { Text("CANCEL") } })
    }
}

@Composable
private fun CardPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()
        .border(1.dp, Bci.Cyan.copy(alpha = .22f), RoundedCornerShape(20.dp))
        .background(Brush.linearGradient(listOf(Bci.Graphite, Bci.Black.copy(alpha = .96f), Bci.Graphite)), RoundedCornerShape(20.dp))
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = Bci.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        content()
    }
}

@Composable
private fun SettingSwitch(title: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, Modifier.weight(1f), fontSize = 13.sp, color = Bci.White)
        Switch(checked = enabled, onCheckedChange = onChange)
    }
}

/** User-facing telemetry rendering; no tower geolocation inferred from radio identifiers. */
private fun cellDescription(cell: JSONObject): String {
    val radio = cell.optString("radio", "UNKNOWN")
    val mcc = cell.optString("mcc", "?")
    val mnc = cell.optString("mnc", "?")
    val id = when (radio) {
        "NR" -> "NCI ${cell.optString("nci", "?")}"
        "LTE" -> "CI ${cell.optString("ci", "?")}"
        else -> "CID ${cell.optString("cid", "?")}"
    }
    return "$radio · $mcc/$mnc · $id · TAC/LAC ${cell.optString(if (radio == "LTE" || radio == "NR") "tac" else "lac", "?")}"
}

private fun cellSignalDescription(cell: JSONObject): String =
    "dBm ${cell.optString("signal_dbm", "?")} · RSRP ${cell.optString("rsrp_dbm", "?")} · RSRQ ${cell.optString("rsrq_db", "?")}"

private fun servingSummary(cells: org.json.JSONArray?): String {
    if (cells == null || cells.length() == 0) return "none / unobserved"
    return (0 until minOf(cells.length(), 3)).mapNotNull { i ->
        cells.optJSONObject(i)?.let { cell ->
            val rat = cell.optString("radio", "?")
            val key = when (rat) {
                "LTE" -> cell.optString("ci", "?")
                "NR" -> cell.optString("nci", "?")
                else -> cell.optString("cid", "?")
            }
            "$rat:$key"
        }
    }.joinToString(", ")
}
