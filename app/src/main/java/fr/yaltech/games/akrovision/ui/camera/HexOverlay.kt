package fr.yaltech.games.akrovision.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.yaltech.games.akrovision.detection.HexSample
import fr.yaltech.games.akrovision.detection.sampleHex
import fr.yaltech.games.akrovision.model.AnalysisResult
import fr.yaltech.games.akrovision.model.DistrictColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HexOverlay(
    hexGridState: HexGridState,
    analysis: AnalysisResult?,
    heights: Map<Pair<Int, Int>, Int>,
    cellStars: Map<Pair<Int, Int>, Int>,
    selectedCell: Pair<Int, Int>?,
    screenW: Float,
    screenH: Float,
    onGridStateChanged: (HexGridState) -> Unit,
    onCellTapped: (Int, Int) -> Unit,
    onHexColorsComputed: (Array<Array<DistrictColor?>>) -> Unit,
    onHexMultipliersComputed: (Array<Array<Boolean>>) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val currentState = rememberUpdatedState(hexGridState)
    val currentOnChanged = rememberUpdatedState(onGridStateChanged)
    val currentOnTapped = rememberUpdatedState(onCellTapped)

    // screenW et screenH sont des clés : si l'écran pivote, on recalcule les échantillons
    val hexSamples = remember(analysis, hexGridState, screenW, screenH) {
        if (hexGridState.hexRadius <= 0f || analysis == null || screenW <= 0f || screenH <= 0f)
            return@remember Array(0) { arrayOf<HexSample>() }
        val R = hexGridState.effectiveRadius
        Array(hexGridState.numRows) { row ->
            Array(hexGridState.numCols) { col ->
                sampleHex(analysis, hexGridState.centerOf(row, col), R, screenW, screenH)
            }
        }
    }

    SideEffect {
        if (hexSamples.isNotEmpty()) {
            onHexColorsComputed(Array(hexSamples.size) { r ->
                Array(hexSamples[r].size) { c -> hexSamples[r][c].color }
            })
            onHexMultipliersComputed(Array(hexSamples.size) { r ->
                Array(hexSamples[r].size) { c -> hexSamples[r][c].isMultiplier }
            })
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, rotDelta ->
                    val s = currentState.value
                    val newScale = (s.scale * zoom).coerceIn(0.30f, 6f)
                    val newRotation = (s.rotationDegrees + rotDelta).coerceIn(-45f, 45f)
                    val newOffset = centroid + (s.panOffset - centroid) * zoom + pan
                    currentOnChanged.value(s.copy(panOffset = newOffset, scale = newScale, rotationDegrees = newRotation))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    currentState.value.hexAt(offset)
                        ?.let { (r, c) -> currentOnTapped.value(r, c) }
                }
            }
    ) {
        if (hexGridState.hexRadius <= 0f) return@Canvas
        val R = hexGridState.effectiveRadius
        val rotDeg = hexGridState.rotationDegrees

        for (row in 0 until hexGridState.numRows) {
            for (col in 0 until hexGridState.numCols) {
                val center = hexGridState.centerOf(row, col)
                if (center.x < -R * 2 || center.x > screenW + R * 2 ||
                    center.y < -R * 2 || center.y > screenH + R * 2) continue

                val sample = hexSamples.getOrNull(row)?.getOrNull(col)
                val district = sample?.color
                val isMultiplier = sample?.isMultiplier == true
                val height = heights[row to col] ?: 1
                val stars = cellStars[row to col]
                val isSelected = selectedCell?.first == row && selectedCell.second == col

                drawHexagon(
                    center = center,
                    R = R * 0.90f,
                    rotationDeg = rotDeg,
                    district = district,
                    height = height,
                    isSelected = isSelected,
                    isMultiplier = isMultiplier,
                    stars = stars,
                    textMeasurer = textMeasurer
                )
            }
        }
    }
}

private fun DrawScope.drawHexagon(
    center: Offset,
    R: Float,
    rotationDeg: Float,
    district: DistrictColor?,
    height: Int,
    isSelected: Boolean,
    isMultiplier: Boolean,
    stars: Int?,
    textMeasurer: TextMeasurer
) {
    val path = hexPath(center, R, rotationDeg)

    if (isMultiplier && district != null) {
        drawPath(path, color = Color(district.argb).copy(alpha = 0.78f))
        drawPath(
            path,
            color = if (isSelected) Color.Yellow else Color(0xFFFFD700.toInt()),
            style = Stroke(width = if (isSelected) 4.dp.toPx() else 2.5.dp.toPx())
        )
    } else {
        if (district != null) {
            val alpha = if (district.givesPoints) 0.42f else 0.15f
            drawPath(path, color = Color(district.argb).copy(alpha = alpha))
        }
        drawPath(
            path,
            color = if (isSelected) Color.Yellow else Color.White.copy(alpha = 0.40f),
            style = Stroke(width = if (isSelected) 3.dp.toPx() else 1.5.dp.toPx())
        )
    }

    if (R < 16.dp.toPx()) return

    if (isMultiplier && district != null) {
        val label = if (stars != null) "×$stars" else "×?"
        drawText(
            textMeasurer = textMeasurer,
            text = label,
            topLeft = Offset(center.x - R * 0.28f, center.y - R * 0.30f),
            style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        )
    } else if (district != null && district.givesPoints) {
        drawText(
            textMeasurer = textMeasurer,
            text = district.label.take(3),
            topLeft = Offset(center.x - R * 0.55f, center.y - R * 0.42f),
            style = TextStyle(color = Color.White, fontSize = 8.sp)
        )
        if (height > 1) {
            drawText(
                textMeasurer = textMeasurer,
                text = "▲$height",
                topLeft = Offset(center.x - R * 0.35f, center.y + R * 0.10f),
                style = TextStyle(color = Color.Yellow, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            )
        }
    } else if (!isMultiplier && height > 1) {
        drawText(
            textMeasurer = textMeasurer,
            text = "▲$height",
            topLeft = Offset(center.x - R * 0.30f, center.y - R * 0.20f),
            style = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 8.sp)
        )
    }
}

// Le rotationDeg est la rotation de la grille : chaque hexagone tourne sur lui-même
// du même angle pour que la forme reste cohérente avec l'alignement de la grille.
private fun hexPath(center: Offset, R: Float, rotationDeg: Float = 0f): Path = Path().apply {
    for (i in 0..5) {
        val angle = (60f * i - 30f + rotationDeg) * PI.toFloat() / 180f
        val x = center.x + R * cos(angle)
        val y = center.y + R * sin(angle)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
