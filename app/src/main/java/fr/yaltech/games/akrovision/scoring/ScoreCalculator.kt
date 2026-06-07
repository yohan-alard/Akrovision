package fr.yaltech.games.akrovision.scoring

import fr.yaltech.games.akrovision.model.DistrictColor

data class ColorScore(
    val district: DistrictColor,
    val largestGroupValue: Int,  // somme des hauteurs du plus grand groupe connexe
    val laurels: Int,
    val score: Int               // largestGroupValue × laurels
)

data class ScoreResult(
    val colorScores: List<ColorScore>,
    val total: Int
)

object ScoreCalculator {

    fun calculate(
        grid: Array<Array<DistrictColor?>>,
        heights: Map<Pair<Int, Int>, Int>,
        laurels: Map<DistrictColor, Int>,
        // Fonction de voisinage : (row, col) → liste des voisins valides
        // Par défaut 4-directions, passer hexGridState::hexNeighbors pour la grille hex
        neighborsOf: (Int, Int) -> List<Pair<Int, Int>> = { row, col ->
            val rows = grid.size
            val cols = if (rows > 0) grid[0].size else 0
            listOf(row - 1 to col, row + 1 to col, row to col - 1, row to col + 1)
                .filter { (r, c) -> r in 0 until rows && c in 0 until cols }
        }
    ): ScoreResult {
        val rows = grid.size
        val cols = if (rows > 0) grid[0].size else 0

        val colorScores = DistrictColor.entries
            .filter { it.givesPoints }
            .map { district ->
                val groupValue = largestGroupValue(grid, heights, rows, cols, district, neighborsOf)
                val districtLaurels = laurels[district] ?: 0
                ColorScore(district, groupValue, districtLaurels, groupValue * districtLaurels)
            }

        return ScoreResult(colorScores, colorScores.sumOf { it.score })
    }

    private fun largestGroupValue(
        grid: Array<Array<DistrictColor?>>,
        heights: Map<Pair<Int, Int>, Int>,
        rows: Int,
        cols: Int,
        district: DistrictColor,
        neighborsOf: (Int, Int) -> List<Pair<Int, Int>>
    ): Int {
        val visited = Array(rows) { BooleanArray(cols) }
        var maxValue = 0

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (grid[r][c] == district && !visited[r][c]) {
                    val value = bfs(grid, heights, visited, r, c, district, neighborsOf)
                    if (value > maxValue) maxValue = value
                }
            }
        }
        return maxValue
    }

    private fun bfs(
        grid: Array<Array<DistrictColor?>>,
        heights: Map<Pair<Int, Int>, Int>,
        visited: Array<BooleanArray>,
        startRow: Int,
        startCol: Int,
        district: DistrictColor,
        neighborsOf: (Int, Int) -> List<Pair<Int, Int>>
    ): Int {
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(startRow to startCol)
        visited[startRow][startCol] = true
        var groupValue = 0

        while (queue.isNotEmpty()) {
            val (row, col) = queue.removeFirst()
            groupValue += heights[row to col] ?: 1

            for ((nr, nc) in neighborsOf(row, col)) {
                if (!visited[nr][nc] && grid[nr][nc] == district) {
                    visited[nr][nc] = true
                    queue.add(nr to nc)
                }
            }
        }
        return groupValue
    }
}
