package fr.yaltech.games.akrovision.ui.camera

import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

data class HexGridState(
    val numCols: Int = 9,
    val numRows: Int = 16,
    val hexRadius: Float = 0f,   // px, initialisé depuis la taille d'écran
    val panOffset: Offset = Offset.Zero,
    val scale: Float = 1f
) {
    val effectiveRadius get() = hexRadius * scale

    // Centre de la cellule (row, col) en coordonnées écran
    fun centerOf(row: Int, col: Int): Offset {
        val R = effectiveRadius
        val x = (col + 0.5f * (row % 2)) * R * SQRT3 + panOffset.x
        val y = row * R * 1.5f + R + panOffset.y
        return Offset(x, y)
    }

    // Cellule hexagonale sous le point d'écran, ou null si hors grille / grille non initialisée
    fun hexAt(pos: Offset): Pair<Int, Int>? {
        val R = effectiveRadius
        if (R <= 0f) return null
        val approxRow = ((pos.y - panOffset.y - R) / (R * 1.5f)).toInt().coerceIn(0, numRows - 1)
        val approxCol = ((pos.x - panOffset.x) / (R * SQRT3)).toInt().coerceIn(0, numCols - 1)

        var minDist = Float.MAX_VALUE
        var best: Pair<Int, Int>? = null

        for (r in (approxRow - 2).coerceAtLeast(0)..(approxRow + 2).coerceAtMost(numRows - 1)) {
            for (c in (approxCol - 2).coerceAtLeast(0)..(approxCol + 2).coerceAtMost(numCols - 1)) {
                val center = centerOf(r, c)
                val dist = (pos - center).getDistance()
                if (dist < minDist && dist < R) { minDist = dist; best = r to c }
            }
        }
        return best
    }

    // Voisins hexagonaux (offset grid, hex pointu vers le haut)
    fun hexNeighbors(row: Int, col: Int): List<Pair<Int, Int>> {
        val n = if (row % 2 == 0) listOf(
            row - 1 to col - 1, row - 1 to col,
            row     to col - 1, row     to col + 1,
            row + 1 to col - 1, row + 1 to col
        ) else listOf(
            row - 1 to col,     row - 1 to col + 1,
            row     to col - 1, row     to col + 1,
            row + 1 to col,     row + 1 to col + 1
        )
        return n.filter { (r, c) -> r in 0 until numRows && c in 0 until numCols }
    }

    companion object {
        val SQRT3 = sqrt(3f)
    }
}
