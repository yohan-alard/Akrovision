package fr.yaltech.games.akrovision.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import fr.yaltech.games.akrovision.model.AnalysisResult
import fr.yaltech.games.akrovision.model.DistrictColor
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HexOverlay(
    hexGridState: HexGridState,
    analysis: AnalysisResult?,
    heights: Map<Pair<Int, Int>, Int>,
    selectedCell: Pair<Int, Int>?,
    screenW: Float,
    screenH: Float,
    onGridStateChanged: (HexGridState) -> Unit,
    onCellTapped: (Int, Int) -> Unit,
    onHexColorsComputed: (Array<Array<DistrictColor?>>) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()

    // rememberUpdatedState : accès aux dernières valeurs sans recréer les blocs pointerInput
    val currentState = rememberUpdatedState(hexGridState)
    val currentOnChanged = rememberUpdatedState(onGridStateChanged)
    val currentOnTapped = rememberUpdatedState(onCellTapped)

    // Calcule les couleurs par hexagone à partir de la carte de couleurs et de la grille
    val hexColors = remember(analysis, hexGridState) {
        if (hexGridState.hexRadius <= 0f || screenW <= 0f || screenH <= 0f)
            return@remember Array(0) { arrayOf<DistrictColor?>() }
        Array(hexGridState.numRows) { row ->
            Array<DistrictColor?>(hexGridState.numCols) { col ->
                val center = hexGridState.centerOf(row, col)
                analysis?.colorAt(center.x / screenW, center.y / screenH)
            }
        }
    }

    LaunchedEffect(hexColors) {
        if (hexColors.isNotEmpty()) onHexColorsComputed(hexColors)
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            // pointerInput(Unit) → le détecteur de gestes reste vivant, rememberUpdatedState
            // fournit les valeurs fraîches à chaque événement
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val s = currentState.value
                    val newScale = (s.scale * zoom).coerceIn(0.30f, 6f)
                    currentOnChanged.value(
                        s.copy(panOffset = s.panOffset + pan, scale = newScale)
                    )
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

        for (row in 0 until hexGridState.numRows) {
            for (col in 0 until hexGridState.numCols) {
                val center = hexGridState.centerOf(row, col)
                if (center.x < -R * 2 || center.x > screenW + R * 2 ||
                    center.y < -R * 2 || center.y > screenH + R * 2) continue

                val district = hexColors.getOrNull(row)?.getOrNull(col)
                val height = heights[row to col] ?: 1
                val isSelected = selectedCell?.first == row && selectedCell.second == col

                drawHexagon(center, R * 0.90f, district, height, isSelected, textMeasurer)
            }
        }
    }
}

private fun DrawScope.drawHexagon(
    center: Offset,
    R: Float,
    district: DistrictColor?,
    height: Int,
    isSelected: Boolean,
    textMeasurer: TextMeasurer
) {
    val path = hexPath(center, R)

    if (district != null) {
        val alpha = if (district.givesPoints) 0.42f else 0.15f
        drawPath(path, color = Color(district.argb).copy(alpha = alpha))
    }

    drawPath(
        path,
        color = if (isSelected) Color.Yellow else Color.White.copy(alpha = 0.40f),
        style = Stroke(width = if (isSelected) 3.dp.toPx() else 1.5.dp.toPx())
    )

    if (R < 16.dp.toPx()) return

    if (district != null && district.givesPoints) {
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
    } else if (height > 1) {
        drawText(
            textMeasurer = textMeasurer,
            text = "▲$height",
            topLeft = Offset(center.x - R * 0.30f, center.y - R * 0.20f),
            style = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 8.sp)
        )
    }
}

private fun hexPath(center: Offset, R: Float): Path = Path().apply {
    for (i in 0..5) {
        val angle = (60f * i - 30f) * Math.PI.toFloat() / 180f  // pointy-top
        val x = center.x + R * cos(angle)
        val y = center.y + R * sin(angle)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
