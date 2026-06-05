package fr.yaltech.games.akrovision.model

import android.graphics.Color

// Tuiles illustrées : les pixels parasites minoritaires sont écrasés par le comptage dominant
enum class DistrictColor(
    val label: String,
    val argb: Int,
    val hueMin: Float,
    val hueMax: Float,
    val minSat: Float,
    val maxSat: Float = 1.0f,
    val minVal: Float,
    val givesPoints: Boolean = true
) {
    // H=202 S=0.44 — séparé de Casernes par maxSat=0.56
    HABITANTS("Habitants", Color.parseColor("#2196F3"), 197f, 235f, 0.35f, 0.56f, 0.3f),

    // H=40 S=0.50
    MARCHES("Marchés",    Color.parseColor("#FFC107"), 35f,  65f,  0.45f, 1.0f,  0.45f),

    // H=200 S=0.66 — même zone hue qu'Habitants mais saturation plus élevée
    CASERNES("Casernes",  Color.parseColor("#F44336"), 190f, 220f, 0.57f, 1.0f,  0.3f),

    // H=100 S=0.35 V=0.35
    JARDINS("Jardins",    Color.parseColor("#4CAF50"), 95f,  150f, 0.28f, 1.0f,  0.28f),

    // H=260 S=0.30
    TEMPLES("Temples",    Color.parseColor("#9C27B0"), 255f, 300f, 0.25f, 1.0f,  0.2f),

    // H=190 S=0.14 — pierre détectée mais sans points
    PIERRE("Pierre",      Color.parseColor("#9E9E9E"), 175f, 210f, 0.05f, 0.25f, 0.35f, givesPoints = false);

    companion object {
        fun fromPixel(pixel: Int, hsv: FloatArray): DistrictColor? {
            Color.colorToHSV(pixel, hsv)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]

            return entries.firstOrNull { d ->
                hue in d.hueMin..d.hueMax
                    && sat in d.minSat..d.maxSat
                    && value >= d.minVal
            }
        }
    }
}
