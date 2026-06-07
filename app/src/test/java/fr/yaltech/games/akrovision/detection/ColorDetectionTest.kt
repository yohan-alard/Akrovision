package fr.yaltech.games.akrovision.detection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import fr.yaltech.games.akrovision.model.AnalysisResult
import fr.yaltech.games.akrovision.model.DistrictColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests de détection sur les 5 photos de tuiles réelles (src/test/resources/samples/).
 * Utilise Robolectric (BitmapFactory + Color.colorToHSV) — chemin identique à la production.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ColorDetectionTest {

    companion object {
        private const val THRESHOLD = 50   // pixels minimum par tuile
    }

    // ── 5 tests image — un par photo ─────────────────────────────────────────────────────

    // Landscape : 1×TEMPLES + 1×MARCHES + 1×HABITANTS
    @Test fun image6001() = assertTiles("6001.jpg",
        regular = mapOf(
            DistrictColor.TEMPLES   to 1,
            DistrictColor.MARCHES   to 1,
            DistrictColor.HABITANTS to 1
        )
    )

    // Portrait : 1×HABITANTS + 1×PIERRE + multiplicateur JARDINS (×3)
    @Test fun image6002() = assertTiles("6002.jpg",
        regular     = mapOf(DistrictColor.HABITANTS to 1, DistrictColor.PIERRE to 1),
        multipliers = listOf(DistrictColor.JARDINS)
    )

    // Portrait : 2×PIERRE + multiplicateur MARCHES (×2)
    @Test fun image6003() = assertTiles("6003.jpg",
        regular     = mapOf(DistrictColor.PIERRE to 2),
        multipliers = listOf(DistrictColor.MARCHES)
    )

    // Portrait : 1×CASERNES + 1×TEMPLES + 1×HABITANTS
    @Test fun image6004() = assertTiles("6004.jpg",
        regular = mapOf(
            DistrictColor.CASERNES  to 1,
            DistrictColor.TEMPLES   to 1,
            DistrictColor.HABITANTS to 1
        )
    )

    // Carte de départ : 3×PIERRE + multiplicateur HABITANTS (×1)
    @Test fun image6005() = assertTiles("6005.jpg",
        regular     = mapOf(DistrictColor.PIERRE to 3),
        multipliers = listOf(DistrictColor.HABITANTS)
    )

    // ── Logique de détection multiplicateur ──────────────────────────────────────────────

    @Test fun uniformColorMapYieldsMultiplier() {
        val uniformMap = Array(80) { Array<DistrictColor?>(120) { DistrictColor.JARDINS } }
        val result = AnalysisResult(uniformMap, 120, 80, 0, FloatArray(3), null)
        val sample = sampleHex(result, Offset(60f, 40f), R = 10f, screenW = 120f, screenH = 80f)
        assertEquals(DistrictColor.JARDINS, sample.color)
        assertTrue("Couleur uniforme → tuile multiplicateur attendue", sample.isMultiplier)
    }

    @Test fun mixedColorMapDoesNotYieldMultiplier() {
        val mixedMap = Array(80) { row ->
            Array<DistrictColor?>(120) { _ -> if (row < 40) DistrictColor.HABITANTS else null }
        }
        val result = AnalysisResult(mixedMap, 120, 80, 0, FloatArray(3), null)
        val sample = sampleHex(result, Offset(60f, 40f), R = 15f, screenW = 120f, screenH = 80f)
        assertFalse("Couleur mixte → pas une tuile multiplicateur", sample.isMultiplier)
    }

    @Test fun multiplierThresholdIs75Percent() {
        assertEquals(0.75f, MULTIPLIER_THRESHOLD)
    }

    // ── Diagnostics (toujours réussis) ───────────────────────────────────────────────────

    @Test fun diagnosticHsvUnclassified() {
        val hsv = FloatArray(3)
        for (name in listOf("6001", "6002", "6003", "6004", "6005")) {
            val pixels = loadPixels("$name.jpg")
            val count = IntArray(24)
            val sumH  = FloatArray(24); val sumS = FloatArray(24); val sumV = FloatArray(24)
            for (pixel in pixels) {
                if (DistrictColor.fromPixel(pixel, hsv) == null) {
                    val bin = (hsv[0] / 15f).toInt().coerceIn(0, 23)
                    count[bin]++
                    sumH[bin] += hsv[0]; sumS[bin] += hsv[1]; sumV[bin] += hsv[2]
                }
            }
            println("=== $name.jpg : ${count.sum()} px non classés ===")
            count.indices.filter { count[it] > 50 }.sortedByDescending { count[it] }.take(8)
                .forEach { i ->
                    val n = count[i]
                    println("  H %3d°–%3d° : %4d px  avg H=%.0f S=%.2f V=%.2f"
                        .format(i * 15, (i + 1) * 15, n, sumH[i] / n, sumS[i] / n, sumV[i] / n))
                }
        }
    }

    @Test fun diagnosticPrintColorCounts() {
        for (name in listOf("6001", "6002", "6003", "6004", "6005")) {
            val counts = analyzeColors("$name.jpg")
            println("=== $name.jpg ===")
            DistrictColor.entries.forEach { d ->
                val n = counts[d] ?: 0
                if (n > 0) println("  %-12s : %4dpx (%2d%%)".format(d.label, n, n * 100 / 9600))
            }
            val unclassified = 9600 - counts.values.sum()
            println("  %-12s : %4dpx (%2d%%)".format("(aucun)", unclassified, unclassified * 100 / 9600))
        }
    }

    // ─────────────────────────── Helpers ──────────────────────────────────────────────────

    // Vérifie les tuiles ordinaires (par comptage de pixels) et les multiplicateurs (par sampleHex).
    // regular : couleur → nombre de tuiles attendues (N tuiles = N × THRESHOLD pixels minimum).
    // multipliers : liste des couleurs attendues comme multiplicateurs.
    private fun assertTiles(
        filename: String,
        regular: Map<DistrictColor, Int> = emptyMap(),
        multipliers: List<DistrictColor> = emptyList()
    ) {
        val pixels = loadPixels(filename)
        val hsv = FloatArray(3)
        val counts = mutableMapOf<DistrictColor, Int>()
        val colorMap = Array(80) { row ->
            Array(120) { col ->
                DistrictColor.fromPixel(pixels[row * 120 + col], hsv).also { color ->
                    if (color != null) counts[color] = (counts[color] ?: 0) + 1
                }
            }
        }

        val allDetected = counts.entries.sortedByDescending { it.value }
            .joinToString(" | ") { "${it.key.label}=${it.value}px" }
        for ((color, tileCount) in regular) {
            val found = counts[color] ?: 0
            val minPixels = tileCount * THRESHOLD
            assertTrue(
                "${color.label} : ${found}px < ${minPixels}px (${tileCount} tuile(s)). Détectés : [$allDetected]",
                found >= minPixels
            )
        }

        if (multipliers.isNotEmpty()) {
            val result = AnalysisResult(colorMap, 120, 80, 0, FloatArray(3), null)
            for (color in multipliers) {
                var found = false
                outer@ for (y in 8..72 step 4) {
                    for (x in 8..112 step 4) {
                        val s = sampleHex(result, Offset(x.toFloat(), y.toFloat()), 7f, 120f, 80f)
                        if (s.color == color && s.isMultiplier) { found = true; break@outer }
                    }
                }
                assertTrue("Multiplicateur ${color.label} non trouvé dans $filename", found)
            }
        }
    }

    private fun analyzeColors(filename: String): Map<DistrictColor, Int> {
        val pixels = loadPixels(filename)
        val hsv = FloatArray(3)
        val counts = mutableMapOf<DistrictColor, Int>()
        for (pixel in pixels) {
            DistrictColor.fromPixel(pixel, hsv)?.let { d -> counts[d] = (counts[d] ?: 0) + 1 }
        }
        return counts
    }

    private fun loadPixels(filename: String): IntArray {
        val stream = javaClass.getResourceAsStream("/samples/$filename")
            ?: error("Ressource introuvable : /samples/$filename")
        val src = BitmapFactory.decodeStream(stream)
        val scaled = Bitmap.createScaledBitmap(src, 120, 80, false)
        src.recycle()
        val pixels = IntArray(120 * 80)
        scaled.getPixels(pixels, 0, 120, 0, 0, 120, 80)
        scaled.recycle()
        return pixels
    }
}
