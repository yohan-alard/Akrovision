package fr.yaltech.games.akrovision.model

import android.graphics.Color

// Plages HSV à calibrer avec des photos des vraies tuiles du jeu
enum class DistrictColor(
    val label: String,
    val argb: Int,
    val hueMin: Float,
    val hueMax: Float,
    val minSaturation: Float,
    val minValue: Float
) {
    HABITANTS("Habitants", Color.parseColor("#2196F3"), 195f, 235f, 0.4f, 0.3f),
    MARCHES("Marchés",    Color.parseColor("#FFC107"), 40f,  65f,  0.5f, 0.5f),
    CASERNES("Casernes",  Color.parseColor("#F44336"), 0f,   12f,  0.5f, 0.3f),
    JARDINS("Jardins",    Color.parseColor("#4CAF50"), 100f, 150f, 0.4f, 0.3f),
    TEMPLES("Temples",    Color.parseColor("#9C27B0"), 270f, 300f, 0.3f, 0.25f);

    companion object {
        fun fromPixel(pixel: Int, hsv: FloatArray): DistrictColor? {
            Color.colorToHSV(pixel, hsv)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]

            return entries.firstOrNull { d ->
                val hueMatch = when (d) {
                    // Rouge : la teinte HSV fait le tour (345-360 + 0-12)
                    CASERNES -> hue <= d.hueMax || hue >= 345f
                    else -> hue in d.hueMin..d.hueMax
                }
                hueMatch && sat >= d.minSaturation && value >= d.minValue
            }
        }
    }
}
