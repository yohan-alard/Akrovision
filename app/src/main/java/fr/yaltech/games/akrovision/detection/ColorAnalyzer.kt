package fr.yaltech.games.akrovision.detection

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import fr.yaltech.games.akrovision.model.AnalysisResult
import fr.yaltech.games.akrovision.model.DistrictColor

class ColorAnalyzer(
    private val onResult: (AnalysisResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val hsv = FloatArray(3)
    private val MAP_W = 120
    private val MAP_H = 80

    override fun analyze(image: ImageProxy) {
        // try-catch global : protège contre les accès à une ImageProxy déjà fermée
        // (arrive lors d'une rotation d'écran qui détruit le lifecycle)
        try {
            val buffer = image.planes[0].buffer
            val original = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            original.copyPixelsFromBuffer(buffer)

            val small = Bitmap.createScaledBitmap(original, MAP_W, MAP_H, false)
            original.recycle()

            val pixels = IntArray(MAP_W * MAP_H)
            small.getPixels(pixels, 0, MAP_W, 0, 0, MAP_W, MAP_H)
            small.recycle()

            val colorMap = Array(MAP_H) { row ->
                Array(MAP_W) { col ->
                    DistrictColor.fromPixel(pixels[row * MAP_W + col], hsv)
                }
            }

            val (centerHsv, centerDistrict) = analyzeCenter(pixels, MAP_W, MAP_H)
            val rotDeg = image.imageInfo.rotationDegrees

            onResult(AnalysisResult(colorMap, MAP_W, MAP_H, rotDeg, centerHsv, centerDistrict))
        } catch (_: Exception) {
            // Image invalidée pendant la rotation : on ignore silencieusement
        } finally {
            try { image.close() } catch (_: Exception) {}
        }
    }

    private fun analyzeCenter(pixels: IntArray, w: Int, h: Int): Pair<FloatArray, DistrictColor?> {
        val cx = w / 2; val cy = h / 2
        val rx = (w * 0.08f).toInt().coerceAtLeast(2)
        val ry = (h * 0.08f).toInt().coerceAtLeast(2)

        var hSum = 0f; var sSum = 0f; var vSum = 0f; var count = 0
        val counts = mutableMapOf<DistrictColor, Int>()

        for (y in (cy - ry)..(cy + ry)) {
            for (x in (cx - rx)..(cx + rx)) {
                if (x < 0 || x >= w || y < 0 || y >= h) continue
                val pixel = pixels[y * w + x]
                android.graphics.Color.colorToHSV(pixel, hsv)
                hSum += hsv[0]; sSum += hsv[1]; vSum += hsv[2]; count++
                val d = DistrictColor.fromPixel(pixel, hsv)
                if (d != null) counts[d] = (counts[d] ?: 0) + 1
            }
        }

        val avg = if (count > 0) floatArrayOf(hSum / count, sSum / count, vSum / count)
                  else floatArrayOf(0f, 0f, 0f)
        return avg to counts.maxByOrNull { it.value }?.key
    }
}
