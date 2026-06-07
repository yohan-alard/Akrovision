package fr.yaltech.games.akrovision.model

data class AnalysisResult(
    val colorMap: Array<Array<DistrictColor?>>, // [row][col], orientation paysage
    val colorMapWidth: Int,
    val colorMapHeight: Int,
    val rotationDegrees: Int,
    val centerHsv: FloatArray,
    val centerDistrict: DistrictColor?
) {
    // Retourne la couleur détectée à une position normalisée (0-1) de l'écran
    fun colorAt(screenNormX: Float, screenNormY: Float): DistrictColor? {
        val (ix, iy) = when (rotationDegrees) {
            90  -> (1f - screenNormY) to screenNormX
            180 -> (1f - screenNormX) to (1f - screenNormY)
            270 -> screenNormY       to (1f - screenNormX)
            else -> screenNormX      to screenNormY
        }
        val col = (ix * colorMapWidth).toInt().coerceIn(0, colorMapWidth - 1)
        val row = (iy * colorMapHeight).toInt().coerceIn(0, colorMapHeight - 1)
        return if (row in colorMap.indices && col in colorMap[0].indices) colorMap[row][col] else null
    }
}
