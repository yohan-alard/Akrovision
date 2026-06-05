package fr.yaltech.games.akrovision.detection

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import fr.yaltech.games.akrovision.model.DistrictColor

class ColorAnalyzer(
    private val gridCols: Int = 4,
    private val gridRows: Int = 4,
    private val onResult: (Array<Array<DistrictColor?>>) -> Unit
) : ImageAnalysis.Analyzer {

    // Réutilisé à chaque frame pour éviter les allocations
    private val hsv = FloatArray(3)

    override fun analyze(image: ImageProxy) {
        // Construire le bitmap depuis le buffer RGBA
        val buffer = image.planes[0].buffer
        val original = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        original.copyPixelsFromBuffer(buffer)

        // Réduire à 64×48 pour analyser rapidement
        val small = Bitmap.createScaledBitmap(original, 64, 48, false)
        original.recycle()

        val w = small.width
        val h = small.height
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        small.recycle()

        val cellW = w / gridCols
        val cellH = h / gridRows
        val grid = Array(gridRows) { arrayOfNulls<DistrictColor>(gridCols) }

        for (row in 0 until gridRows) {
            for (col in 0 until gridCols) {
                val counts = mutableMapOf<DistrictColor, Int>()

                for (y in (row * cellH) until ((row + 1) * cellH)) {
                    for (x in (col * cellW) until ((col + 1) * cellW)) {
                        val detected = DistrictColor.fromPixel(pixels[y * w + x], hsv)
                        if (detected != null) {
                            counts[detected] = (counts[detected] ?: 0) + 1
                        }
                    }
                }

                grid[row][col] = counts.maxByOrNull { it.value }?.key
            }
        }

        image.close()
        onResult(grid)
    }
}
