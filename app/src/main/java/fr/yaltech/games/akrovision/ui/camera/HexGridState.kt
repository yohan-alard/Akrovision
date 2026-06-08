package fr.yaltech.games.akrovision.ui.camera

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class HexGridState(
    val numCols: Int = 9,
    val numRows: Int = 16,
    val hexRadius: Float = 0f,   // px, initialisé depuis la taille d'écran
    val panOffset: Offset = Offset.Zero,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f  // rotation de la grille en degrés
) {
    val effectiveRadius get() = hexRadius * scale

    // Pivot de rotation : centre géométrique de la grille non-rotée
    fun gridCenter(): Offset {
        val R = effectiveRadius
        val gridW = (numCols * SQRT3 + SQRT3 / 2f) * R
        val gridH = (numRows * 1.5f + 0.5f) * R
        return Offset(panOffset.x + gridW / 2f, panOffset.y + gridH / 2f)
    }

    // Centre de la cellule (row, col) en coordonnées écran — avec rotation appliquée
    fun centerOf(row: Int, col: Int): Offset {
        val R = effectiveRadius
        val rawX = (col + 0.5f * (row % 2)) * R * SQRT3 + panOffset.x
        val rawY = row * R * 1.5f + R + panOffset.y
        if (rotationDegrees == 0f) return Offset(rawX, rawY)
        val gc = gridCenter()
        val dx = rawX - gc.x; val dy = rawY - gc.y
        val rad = rotationDegrees * PI.toFloat() / 180f
        val c = cos(rad); val s = sin(rad)
        return Offset(gc.x + dx * c - dy * s, gc.y + dx * s + dy * c)
    }

    // Cellule hexagonale sous le point d'écran, ou null si hors grille
    fun hexAt(pos: Offset): Pair<Int, Int>? {
        val R = effectiveRadius
        if (R <= 0f) return null

        // On dé-rotate la position du tap pour l'approx initiale (grille non-rotée)
        val queryPos = if (rotationDegrees == 0f) pos else {
            val gc = gridCenter()
            val dx = pos.x - gc.x; val dy = pos.y - gc.y
            val rad = -rotationDegrees * PI.toFloat() / 180f
            val c = cos(rad); val s = sin(rad)
            Offset(gc.x + dx * c - dy * s, gc.y + dx * s + dy * c)
        }

        val approxRow = ((queryPos.y - panOffset.y - R) / (R * 1.5f)).toInt().coerceIn(0, numRows - 1)
        val approxCol = ((queryPos.x - panOffset.x) / (R * SQRT3)).toInt().coerceIn(0, numCols - 1)

        var minDist = Float.MAX_VALUE
        var best: Pair<Int, Int>? = null

        for (r in (approxRow - 2).coerceAtLeast(0)..(approxRow + 2).coerceAtMost(numRows - 1)) {
            for (c in (approxCol - 2).coerceAtLeast(0)..(approxCol + 2).coerceAtMost(numCols - 1)) {
                val center = centerOf(r, c)  // centre roté → coordonnées écran réelles
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
