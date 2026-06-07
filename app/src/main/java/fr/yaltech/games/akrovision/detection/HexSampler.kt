package fr.yaltech.games.akrovision.detection

import androidx.compose.ui.geometry.Offset
import fr.yaltech.games.akrovision.model.AnalysisResult
import fr.yaltech.games.akrovision.model.DistrictColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class HexSample(
    val color: DistrictColor?,
    val isMultiplier: Boolean
)

// Poids total des 7 points : centre×2 + 6 points à 55% du rayon×1
private const val TOTAL_WEIGHT = 8

// Une tuile multiplicateur a un cercle de couleur uniforme.
// Si ≥75% des votes pondérés s'accordent sur une couleur → multiplicateur.
internal const val MULTIPLIER_THRESHOLD = 0.75f

/**
 * Échantillonne 7 points dans un hexagone et détermine :
 * - La couleur dominante (vote majoritaire pondéré)
 * - Si la tuile est un multiplicateur (confiance ≥ MULTIPLIER_THRESHOLD)
 *
 * Les tuiles ordinaires ont des illustrations variées : votes mixtes → confiance faible.
 * Les tuiles multiplicateurs ont un cercle plein uniforme → votes concordants → confiance haute.
 */
fun sampleHex(
    analysis: AnalysisResult,
    center: Offset,
    R: Float,
    screenW: Float,
    screenH: Float
): HexSample {
    if (R <= 0f) return HexSample(null, false)
    val votes = mutableMapOf<DistrictColor, Int>()

    fun vote(x: Float, y: Float, weight: Int = 1) {
        val nx = (x / screenW).coerceIn(0f, 1f)
        val ny = (y / screenH).coerceIn(0f, 1f)
        analysis.colorAt(nx, ny)?.let { d -> votes[d] = (votes[d] ?: 0) + weight }
    }

    vote(center.x, center.y, 2)  // centre : poids double
    for (i in 0..5) {
        val a = (60f * i - 30f) * PI.toFloat() / 180f
        vote(center.x + R * 0.55f * cos(a), center.y + R * 0.55f * sin(a))
    }

    val winner = votes.maxByOrNull { it.value } ?: return HexSample(null, false)
    val confidence = winner.value.toFloat() / TOTAL_WEIGHT
    return HexSample(winner.key, confidence >= MULTIPLIER_THRESHOLD)
}
