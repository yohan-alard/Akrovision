package fr.yaltech.games.akrovision.model

data class AnalysisResult(
    val grid: Array<Array<DistrictColor?>>,
    val centerHsv: FloatArray,         // [H, S, V] — moyenne du centre du viseur
    val centerDistrict: DistrictColor? // couleur reconnue au centre (null = non détectée)
)
