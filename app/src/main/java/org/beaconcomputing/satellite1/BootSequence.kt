// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
// SATELLITE1 v0.1.5 — self-contained Jetpack Compose orbital mission interface.
// Visual animation runs IF AND ONLY IF the user has not disabled system animations.
// No sensor, location, permission, or network access occurs in this UI module.
package org.beaconcomputing.satellite1

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val DeepBlue = Color(0xFF091C2B)
private val AuroraBlue = Color(0xFF0C3245)
private val InstrumentRed = Color(0xFF8E294A)

/** First-launch interface only. A skipped intro never starts background collection. */
@Composable
internal fun SatelliteBootScreen(onSkip: () -> Unit) {
    val phases = rememberInfiniteTransition(label = "bci-orbital-boot")
    val orbit by phases.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7200, easing = LinearEasing)),
        label = "satellite-orbit"
    )
    val radar by phases.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing)),
        label = "radar-sweep"
    )
    val breathe by phases.animateFloat(
        initialValue = 0.18f, targetValue = 0.72f,
        animationSpec = infiniteRepeatable(tween(1550, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse),
        label = "telemetry-beacon"
    )
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(2550, easing = FastOutSlowInEasing)) }

    MaterialTheme(colorScheme = darkColorScheme(primary = Bci.Cyan, background = Bci.Black,
        surface = Bci.Graphite, onBackground = Bci.White, onSurface = Bci.White)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Bci.Black, DeepBlue, Bci.Black))
            )
        ) {
            val sceneHeight = (maxHeight * 0.43f).coerceIn(190.dp, 380.dp)
            Column(
                Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("BEACON COMPUTING INITIATIVE", color = Bci.Cyan,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 2.1.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text("SATELLITE1", color = Bci.White, fontSize = 35.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("ORBITAL FIELD INTELLIGENCE", color = Bci.Muted,
                        fontSize = 10.sp, letterSpacing = 1.2.sp,
                        fontFamily = FontFamily.Monospace)
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.fillMaxWidth().height(sceneHeight)) {
                        drawOrbitalScene(orbit, radar, breathe)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("INITIALIZING LOCAL INTERFACE", color = Bci.Cyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.1.sp)
                    Spacer(Modifier.height(13.dp))
                    LinearProgressIndicator(
                        progress = { progress.value },
                        modifier = Modifier.fillMaxWidth(0.74f).height(3.dp),
                        color = Bci.Cyan, trackColor = Bci.Cyan.copy(alpha = .13f)
                    )
                    Spacer(Modifier.height(9.dp))
                    Text("COLLECTION REQUIRES EXPLICIT CONSENT", color = Bci.Muted,
                        fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center)
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = onSkip, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Bci.Cyan, contentColor = Bci.Black)) {
                        Text("ENTER MISSION CONTROL", letterSpacing = 1.1.sp,
                            fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("DEV SEAL: CJB-DEV-2026  //  0.1.5",
                        fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Bci.Muted)
                }
            }
        }
    }
}

/** Telemetry-backed hero: no invented signal metrics or 'safe network' claims. */
@Composable
internal fun MissionControlHeader(monitoring: Boolean, eventCount: Long, servingChanges: Long) {
    val transitions = rememberInfiniteTransition(label = "bci-live-header")
    val orbit by transitions.animateFloat(0f, 360f,
        infiniteRepeatable(tween(13000, easing = LinearEasing)), label = "header-orbit")
    val pulse by transitions.animateFloat(.25f, .82f,
        infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "header-beacon")
    val statusColor by animateColorAsState(
        targetValue = if (monitoring) Bci.Cyan else Bci.Muted,
        animationSpec = tween(350), label = "service-state"
    )
    val displayEvents by animateIntAsState(
        eventCount.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
        animationSpec = tween(700), label = "vault-counter"
    )
    val displayTransitions by animateIntAsState(
        servingChanges.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
        animationSpec = tween(700), label = "cell-counter"
    )
    Column(
        Modifier.fillMaxWidth()
            .border(1.dp, Bci.Cyan.copy(alpha = .43f), RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(DeepBlue, Bci.Graphite, Bci.Maroon.copy(alpha = .58f))),
                RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("BEACON COMPUTING INITIATIVE  /  MISSION CONTROL",
            color = Bci.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            letterSpacing = .75.sp)
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(Modifier.size(90.dp)) { drawOrbitalScene(orbit, orbit * .53f, pulse, compact = true) }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("SATELLITE1", color = Bci.White, fontSize = 27.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Text(if (monitoring) "●  MONITOR ACTIVE" else "○  MONITOR INACTIVE",
                    color = statusColor, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                Text("EVIDENCE-LED  //  CONSENT-GATED", color = Bci.Muted,
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MissionMetric("EVIDENCE VAULT", displayEvents.toString(), Modifier.weight(1f))
            MissionMetric("CELL TRANSITIONS", displayTransitions.toString(), Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(
            Brush.horizontalGradient(listOf(Bci.Cyan.copy(alpha = .8f), InstrumentRed, Color.Transparent))))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("APOGEE // OBSERVE", color = Bci.Muted, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace)
            Text("CJB-DEV-2026", color = Bci.Muted, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun MissionMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier.border(1.dp, Bci.Cyan.copy(alpha = .19f), RoundedCornerShape(13.dp))
        .background(Bci.Black.copy(alpha = .75f), RoundedCornerShape(13.dp))
        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = Bci.Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
            letterSpacing = .55.sp)
        Text(value, color = Bci.Cyan, fontSize = 24.sp, fontWeight = FontWeight.Black)
    }
}

/** Fully original vector art: no network, sensor, device location or telemetry side effects. */
private fun DrawScope.drawOrbitalScene(phase: Float, radar: Float, pulse: Float, compact: Boolean = false) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val u = min(size.width, size.height)
    val rx = u * if (compact) .42f else .43f
    val ry = u * if (compact) .27f else .28f
    val radius = u * if (compact) .19f else .17f
    val pen = if (compact) 1.2f else 2f

    if (!compact) repeat(45) { n ->
        val x = size.width * ((n * 37 + 13) % 97) / 97f
        val y = size.height * ((n * 29 + 17) % 89) / 89f
        drawCircle(Bci.Cyan.copy(alpha = .15f + (n % 5) * .065f),
            radius = if (n % 7 == 0) 2.3f else 1.2f, center = Offset(x, y))
    }
    drawCircle(Bci.Cyan.copy(alpha = .02f + pulse * .025f), radius = radius * 2.85f, center = c)
    drawCircle(Bci.Cyan.copy(alpha = .04f + pulse * .04f), radius = radius * 1.95f, center = c)
    drawOval(Bci.Cyan.copy(alpha = .27f),
        topLeft = Offset(c.x - rx, c.y - ry), size = Size(rx * 2f, ry * 2f),
        style = Stroke(width = pen))
    drawOval(InstrumentRed.copy(alpha = .46f),
        topLeft = Offset(c.x - rx * .82f, c.y - ry * 1.45f),
        size = Size(rx * 1.64f, ry * 2.9f), style = Stroke(width = pen * .9f))
    drawArc(Bci.Cyan.copy(alpha = .5f + pulse * .3f),
        startAngle = radar - 95f, sweepAngle = 65f, useCenter = false,
        topLeft = Offset(c.x - rx, c.y - ry), size = Size(rx * 2f, ry * 2f),
        style = Stroke(width = pen * 1.65f))

    val ang = phase * (PI / 180).toFloat()
    val position = Offset(c.x + rx * cos(ang), c.y + ry * sin(ang))
    if (sin(ang) < 0f) drawSatellite(position, phase, u, pen, compact)

    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFF47E5F5), Color(0xFF105E82), Color(0xFF071627)),
            center = Offset(c.x - radius * .3f, c.y - radius * .35f), radius = radius * 1.9f),
        radius = radius, center = c
    )
    drawCircle(Bci.Cyan.copy(alpha = .6f), radius = radius, center = c,
        style = Stroke(width = pen))
    val continent = Path().apply {
        moveTo(c.x - radius * .75f, c.y - radius * .4f)
        lineTo(c.x - radius * .25f, c.y - radius * .8f)
        lineTo(c.x + radius * .12f, c.y - radius * .46f)
        lineTo(c.x + radius * .18f, c.y + radius * .14f)
        lineTo(c.x - radius * .24f, c.y + radius * .42f)
        lineTo(c.x - radius * .66f, c.y + radius * .11f)
        close()
    }
    drawPath(continent, Color(0xFF39A6B7).copy(alpha = .63f))
    for (latitude in listOf(-.42f, 0f, .42f)) {
        val y = c.y + latitude * radius
        val half = radius * sqrt(1 - latitude * latitude)
        drawLine(Bci.White.copy(alpha = .23f), Offset(c.x - half, y),
            Offset(c.x + half, y), pen * .7f)
    }
    drawOval(Bci.White.copy(alpha = .21f), topLeft = Offset(c.x - radius * .45f, c.y - radius),
        size = Size(radius * .9f, radius * 2f), style = Stroke(width = pen * .55f))
    drawArc(Bci.White.copy(alpha = .5f), startAngle = 145f, sweepAngle = 90f,
        useCenter = false, topLeft = Offset(c.x - radius, c.y - radius),
        size = Size(radius * 2f, radius * 2f), style = Stroke(width = pen * .8f))

    if (sin(ang) >= 0f) drawSatellite(position, phase, u, pen, compact)
    drawLine(Bci.Cyan.copy(alpha = .3f), c, position, strokeWidth = pen * .65f)
    drawCircle(Bci.Cyan.copy(alpha = pulse), radius = pen * 2.1f, center = position)
}

private fun DrawScope.drawSatellite(pos: Offset, degrees: Float, unit: Float,
    line: Float, compact: Boolean) {
    val body = unit * if (compact) .082f else .073f
    drawCircle(Bci.Cyan.copy(alpha = .10f), radius = body * 2.0f, center = pos)
    rotate(degrees + 90f, pivot = pos) {
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Bci.White, Color(0xFF91B2C0), Bci.Cyan)),
            topLeft = Offset(pos.x - body * .5f, pos.y - body * .38f),
            size = Size(body, body * .76f), cornerRadius = CornerRadius(body * .12f)
        )
        for (side in listOf(-1f, 1f)) {
            val x = pos.x + side * body * 1.05f
            drawRoundRect(Color(0xFF225583),
                topLeft = Offset(x - body * .42f, pos.y - body * .5f),
                size = Size(body * .84f, body), cornerRadius = CornerRadius(body * .05f))
            drawLine(Bci.Cyan.copy(alpha = .95f),
                Offset(x, pos.y - body * .5f), Offset(x, pos.y + body * .5f), line)
            drawLine(Bci.Cyan.copy(alpha = .8f), Offset(x - body * .42f, pos.y),
                Offset(x + body * .42f, pos.y), line * .65f)
            drawLine(Bci.White.copy(alpha = .8f),
                Offset(pos.x + side * body * .5f, pos.y),
                Offset(x - side * body * .43f, pos.y), line)
        }
        drawLine(Bci.White, Offset(pos.x, pos.y - body * .4f),
            Offset(pos.x, pos.y - body * .95f), strokeWidth = line)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF05080D)
@Composable
private fun SatelliteBootPreview() { SatelliteBootScreen(onSkip = {}) }

@Preview(showBackground = true, backgroundColor = 0xFF05080D)
@Composable
private fun SatelliteMissionPreview() {
    MaterialTheme(colorScheme = darkColorScheme(primary = Bci.Cyan)) {
        MissionControlHeader(monitoring = true, eventCount = 1440, servingChanges = 5)
    }
}
