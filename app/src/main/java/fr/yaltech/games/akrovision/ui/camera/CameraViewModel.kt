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

    // Appelé depuis l'UI quand la taille d'écran est connue, initialise l'hexRadius
    fun initHexGrid(screenWidthPx: Float, screenHeightPx: Float) {
        if (_hexGridState.value.hexRadius > 0f) return
        val state = _hexGridState.value
        val R = screenWidthPx / (state.numCols * HexGridState.SQRT3 + HexGridState.SQRT3 / 2f)
        val gridH = state.numRows * R * 1.5f + R * 0.5f
        val initY = ((screenHeightPx - gridH) / 2f).coerceAtLeast(0f)
        _hexGridState.value = state.copy(
            hexRadius = R,
            panOffset = androidx.compose.ui.geometry.Offset(0f, initY)
        )
    }

    fun updateHexGrid(state: HexGridState) { _hexGridState.value = state }

    // Mise à jour des couleurs hex depuis l'UI (appelé à chaque frame d'analyse)
    fun updateHexColors(colors: Array<Array<DistrictColor?>>) { _hexColors.value = colors }

    // Snapshot figé avant de naviguer vers l'écran de score
    fun snapshotHexColors(colors: Array<Array<DistrictColor?>>) { _hexColors.value = colors }

    fun getHexColors(): Array<Array<DistrictColor?>> = _hexColors.value

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
