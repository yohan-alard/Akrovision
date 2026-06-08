package fr.yaltech.games.akrovision.ui.camera

import androidx.lifecycle.ViewModel
import fr.yaltech.games.akrovision.model.AnalysisResult
import fr.yaltech.games.akrovision.model.DistrictColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CameraViewModel : ViewModel() {

    private val _analysis = MutableStateFlow<AnalysisResult?>(null)
    val analysis: StateFlow<AnalysisResult?> = _analysis.asStateFlow()

    private val _calibrationMode = MutableStateFlow(false)
    val calibrationMode: StateFlow<Boolean> = _calibrationMode.asStateFlow()

    private val _hexGridState = MutableStateFlow(HexGridState())
    val hexGridState: StateFlow<HexGridState> = _hexGridState.asStateFlow()

    private val _heights = MutableStateFlow<Map<Pair<Int, Int>, Int>>(emptyMap())
    val heights: StateFlow<Map<Pair<Int, Int>, Int>> = _heights.asStateFlow()

    private val _selectedCell = MutableStateFlow<Pair<Int, Int>?>(null)
    val selectedCell: StateFlow<Pair<Int, Int>?> = _selectedCell.asStateFlow()

    private val _laurels = MutableStateFlow(
        DistrictColor.entries.filter { it.givesPoints }.associate { it to 0 }
    )
    val laurels: StateFlow<Map<DistrictColor, Int>> = _laurels.asStateFlow()

    // Snapshot des couleurs hex pour le calcul de score et le BFS
    private val _hexColors = MutableStateFlow<Array<Array<DistrictColor?>>>(emptyArray())
    val hexColors: StateFlow<Array<Array<DistrictColor?>>> = _hexColors.asStateFlow()

    fun updateAnalysis(result: AnalysisResult) { _analysis.value = result }
    fun toggleCalibration() { _calibrationMode.value = !_calibrationMode.value }
    fun selectCell(row: Int, col: Int) { _selectedCell.value = row to col }
    fun clearSelection() { _selectedCell.value = null }
    fun heightAt(row: Int, col: Int) = _heights.value[row to col] ?: 1

    // (Ré)initialise la grille hex selon les dimensions de l'écran.
    // Se réexécute si la taille change de plus de 5 % (ex : rotation d'écran).
    fun initHexGrid(screenWidthPx: Float, screenHeightPx: Float) {
        val state = _hexGridState.value
        val R = screenWidthPx / (state.numCols * HexGridState.SQRT3 + HexGridState.SQRT3 / 2f)
        if (state.hexRadius > 0f && kotlin.math.abs(state.hexRadius - R) / state.hexRadius < 0.05f) return
        val gridH = state.numRows * R * 1.5f + R * 0.5f
        val initY = ((screenHeightPx - gridH) / 2f).coerceAtLeast(0f)
        _hexGridState.value = HexGridState(
            numCols = state.numCols,
            numRows = state.numRows,
            hexRadius = R,
            panOffset = androidx.compose.ui.geometry.Offset(0f, initY),
            scale = 1f
        )
    }

    fun updateHexGrid(state: HexGridState) { _hexGridState.value = state }

    // Analyse la colorMap pour trouver la boîte englobante des tuiles détectées,
    // puis ajuste automatiquement le zoom et la position de la grille pour couvrir cette zone.
    fun snapGridToTiles(analysis: fr.yaltech.games.akrovision.model.AnalysisResult, screenW: Float, screenH: Float) {
        val state = _hexGridState.value
        if (state.hexRadius <= 0f || screenW <= 0f || screenH <= 0f) return

        var minSX = 1f; var maxSX = 0f
        var minSY = 1f; var maxSY = 0f
        var count = 0

        for (row in analysis.colorMap.indices) {
            for (col in analysis.colorMap[row].indices) {
                analysis.colorMap[row][col] ?: continue
                val camNormX = (col + 0.5f) / analysis.colorMapWidth
                val camNormY = (row + 0.5f) / analysis.colorMapHeight
                val (sx, sy) = analysis.cameraToScreen(camNormX, camNormY)
                if (sx < minSX) minSX = sx; if (sx > maxSX) maxSX = sx
                if (sy < minSY) minSY = sy; if (sy > maxSY) maxSY = sy
                count++
            }
        }

        if (count < 80) return  // pas assez de pixels pour cadrer avec confiance

        val margin = 1.25f  // marge autour des tuiles détectées
        val boxW = (maxSX - minSX) * screenW
        val boxH = (maxSY - minSY) * screenH
        val boxCX = ((minSX + maxSX) / 2f) * screenW
        val boxCY = ((minSY + maxSY) / 2f) * screenH

        val rFromW = boxW * margin / (state.numCols * HexGridState.SQRT3 + HexGridState.SQRT3 / 2f)
        val rFromH = boxH * margin / (state.numRows * 1.5f + 0.5f)
        val newEffR = minOf(rFromW, rFromH).coerceIn(state.hexRadius * 0.1f, state.hexRadius * 5f)

        val gridW = (state.numCols * HexGridState.SQRT3 + HexGridState.SQRT3 / 2f) * newEffR
        val gridH = (state.numRows * 1.5f + 0.5f) * newEffR

        _hexGridState.value = state.copy(
            scale = newEffR / state.hexRadius,
            panOffset = androidx.compose.ui.geometry.Offset(boxCX - gridW / 2f, boxCY - gridH / 2f)
        )
    }

    // Mise à jour des couleurs hex depuis l'UI (appelé à chaque frame d'analyse)
    fun updateHexColors(colors: Array<Array<DistrictColor?>>) { _hexColors.value = colors }

    // ─── Multiplicateurs ──────────────────────────────────────────────────────────────────

    private val _hexMultipliers = MutableStateFlow<Array<Array<Boolean>>>(emptyArray())
    val hexMultipliers: StateFlow<Array<Array<Boolean>>> = _hexMultipliers.asStateFlow()

    // Étoiles confirmées par l'utilisateur pour chaque cellule multiplicateur
    private val _cellStars = MutableStateFlow<Map<Pair<Int, Int>, Int>>(emptyMap())
    val cellStars: StateFlow<Map<Pair<Int, Int>, Int>> = _cellStars.asStateFlow()

    fun updateHexMultipliers(m: Array<Array<Boolean>>) { _hexMultipliers.value = m }

    fun isMultiplierCell(row: Int, col: Int): Boolean =
        _hexMultipliers.value.getOrNull(row)?.getOrNull(col) == true

    fun setCellStars(row: Int, col: Int, stars: Int) {
        _cellStars.value = _cellStars.value + (row to col to stars)
        _selectedCell.value = null
    }

    // ─── Navigation vers le score ─────────────────────────────────────────────────────────

    fun getHexColors(): Array<Array<DistrictColor?>> = _hexColors.value

    // Fige les couleurs et initialise les lauriers depuis les tuiles multiplicateurs détectées.
    // L'utilisateur peut encore ajuster sur l'écran score.
    fun snapshotAndInitLaurels(hexColors: Array<Array<DistrictColor?>>) {
        _hexColors.value = hexColors
        val computed = mutableMapOf<DistrictColor, Int>()
        for ((cell, stars) in _cellStars.value) {
            val (r, c) = cell
            hexColors.getOrNull(r)?.getOrNull(c)?.let { d ->
                if (d.givesPoints) computed[d] = (computed[d] ?: 0) + stars
            }
        }
        _laurels.value = DistrictColor.entries
            .filter { it.givesPoints }
            .associate { it to (computed[it] ?: 0) }
    }

    // Applique la hauteur à tout le groupe connexe de la même couleur
    fun setHeight(row: Int, col: Int, height: Int, hexColors: Array<Array<DistrictColor?>>) {
        val color = hexColors.getOrNull(row)?.getOrNull(col)
        val group = if (color != null) findConnectedHexGroup(row, col, color, hexColors)
                    else setOf(row to col)
        val updated = _heights.value.toMutableMap()
        group.forEach { (r, c) -> updated[r to c] = height }
        _heights.value = updated
        _selectedCell.value = null
    }

    fun setLaurels(district: DistrictColor, count: Int) {
        _laurels.value = _laurels.value + (district to count.coerceAtLeast(0))
    }

    private fun findConnectedHexGroup(
        startRow: Int, startCol: Int,
        color: DistrictColor,
        hexColors: Array<Array<DistrictColor?>>
    ): Set<Pair<Int, Int>> {
        val state = _hexGridState.value
        val visited = mutableSetOf(startRow to startCol)
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(startRow to startCol)
        while (queue.isNotEmpty()) {
            val (r, c) = queue.removeFirst()
            for ((nr, nc) in state.hexNeighbors(r, c)) {
                if (nr to nc !in visited && hexColors.getOrNull(nr)?.getOrNull(nc) == color) {
                    visited.add(nr to nc)
                    queue.add(nr to nc)
                }
            }
        }
        return visited
    }
}
