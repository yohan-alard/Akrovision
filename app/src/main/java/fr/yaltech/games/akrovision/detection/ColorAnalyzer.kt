package fr.yaltech.games.akrovision.detection

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import fr.yaltech.games.akrovision.model.AnalysisResult
import fr.yaltech.games.akrovision.model.DistrictColor

class ColorAnalyzer(
    private val gridCols: Int = 4,
    private val gridRows: Int = 4,
    private val onResult: (AnalysisResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val hsv = FloatArray(3)

    // Zone centrale du viseur : 20% de la largeur/hauteur de l'image réduite
    private val centerRadiusFraction = 0.10f

    override fun analyze(image: ImageProxy) {
        val buffer = image.planes[0].buffer
        val original = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        original.copyPixelsFromBuffer(buffer)

        val small = Bitmap.createScaledBitmap(original, 64, 48, false)
        original.recycle()

        val w = small.width
        val h = small.height
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        small.recycle()

        val grid = analyzeGrid(pixels, w, h)
        val (centerHsv, centerDistrict) = analyzeCenter(pixels, w, h)

        image.close()
        onResult(AnalysisResult(grid, centerHsv, centerDistrict))
    }

    private fun analyzeGrid(pixels: IntArray, w: Int, h: Int): Array<Array<DistrictColor?>> {
        val cellW = w / gridCols
        val cellH = h / gridRows
        val grid = Array(gridRows) { arrayOfNulls<DistrictColor>(gridCols) }

        for (row in 0 until gridRows) {
            for (col in 0 until gridCols) {
                val counts = mutableMapOf<DistrictColor, Int>()
                for (y in (row * cellH) until ((row + 1) * cellH)) {
                    for (x in (col * cellW) until ((col + 1) * cellW)) {
                        val detected = DistrictColor.fromPixel(pixels[y * w + x], hsv)
                        if (detected != null) counts[detected] = (counts[detected] ?: 0) + 1
                    }
                }
                grid[row][col] = counts.maxByOrNull { it.value }?.key
            }
        }
        return grid
    }

    private fun analyzeCenter(pixels: IntArray, w: Int, h: Int): Pair<FloatArray, DistrictColor?> {
        val cx = w / 2
        val cy = h / 2
        val rx = (w * centerRadiusFraction).toInt().coerceAtLeast(2)
        val ry = (h * centerRadiusFraction).toInt().coerceAtLeast(2)

        var hSum = 0f; var sSum = 0f; var vSum = 0f; var count = 0
        val counts = mutableMapOf<DistrictColor, Int>()

        for (y in (cy - ry)..(cy + ry)) {
            for (x in (cx - rx)..(cx + rx)) {
                if (x < 0 || x >= w || y < 0 || y >= h) continue
                val pixel = pixels[y * w + x]
                android.graphics.Color.colorToHSV(pixel, hsv)
                hSum += hsv[0]; sSum += hsv[1]; vSum += hsv[2]; count++
                val detected = DistrictColor.fromPixel(pixel, hsv)
                if (detected != null) counts[detected] = (counts[detected] ?: 0) + 1
            }
        }

        val avgHsv = if (count > 0) floatArrayOf(hSum / count, sSum / count, vSum / count)
                     else floatArrayOf(0f, 0f, 0f)
        val detected = counts.maxByOrNull { it.value }?.key
        return avgHsv to detected
    }
}
