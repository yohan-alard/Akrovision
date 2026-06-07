package fr.yaltech.games.akrovision.model

import android.graphics.Color

// Tuiles illustrées : les pixels parasites minoritaires sont écrasés par le comptage dominant
// hueMin > hueMax signifie un chevauchement du 0° (ex: orange H=340→30).
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
    // H≈202 S≈0.44 — bleu ciel ; pool saturé du mult. HABITANTS couvert par maxSat=1.0
    HABITANTS("Habitants", Color.parseColor("#2196F3"), 197f, 235f, 0.35f, 1.0f,  0.3f),

    // H≈40 S≈0.50 — jaune/ambre
    MARCHES("Marchés",    Color.parseColor("#FFC107"), 35f,  65f,  0.45f, 1.0f,  0.45f),

    // H≈0-20 / 345-360 — orange/rouge, chevauche 0° → hueMin > hueMax
    // Calibré sur 6004 : H=7 S=0.48 V=0.68 (412px), H=354 S=0.59 V=0.56 (223px), H=19 S=0.39 V=0.72 (146px)
    CASERNES("Casernes",  Color.parseColor("#F44336"), 340f, 30f,  0.35f, 1.0f,  0.45f),

    // H≈100-130 S≈0.35 V≈0.35 — vert
    JARDINS("Jardins",    Color.parseColor("#4CAF50"), 95f,  150f, 0.28f, 1.0f,  0.28f),

    // H≈260-280 S≈0.30 — violet
    TEMPLES("Temples",    Color.parseColor("#9C27B0"), 255f, 300f, 0.25f, 1.0f,  0.2f),

    // H≈175-210 S≈0.05-0.25 — gris bleuté, pas de points
    PIERRE("Pierre",      Color.parseColor("#9E9E9E"), 175f, 210f, 0.05f, 0.25f, 0.35f, givesPoints = false);

    companion object {
        // Logique pure Kotlin — testable sur JVM sans Android.
        // Supporte le chevauchement du 0° (hueMin > hueMax, ex: CASERNES orange).
        fun fromHsv(h: Float, s: Float, v: Float): DistrictColor? =
            entries.firstOrNull { d ->
                val hueOk = if (d.hueMin <= d.hueMax) h in d.hueMin..d.hueMax
                            else h >= d.hueMin || h <= d.hueMax
                hueOk && s in d.minSat..d.maxSat && v >= d.minVal
            }

        fun fromPixel(pixel: Int, hsv: FloatArray): DistrictColor? {
            Color.colorToHSV(pixel, hsv)
            return fromHsv(hsv[0], hsv[1], hsv[2])
        }
    }
}
