package fr.yaltech.games.akrovision.ui.score

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.yaltech.games.akrovision.model.DistrictColor
import fr.yaltech.games.akrovision.scoring.ColorScore
import fr.yaltech.games.akrovision.scoring.ScoreCalculator
import fr.yaltech.games.akrovision.ui.camera.CameraViewModel

@Composable
fun ScoreScreen(
    viewModel: CameraViewModel,
    onBack: () -> Unit
) {
    val heights by viewModel.heights.collectAsStateWithLifecycle()
    val laurels by viewModel.laurels.collectAsStateWithLifecycle()
    val hexGridState by viewModel.hexGridState.collectAsStateWithLifecycle()

    // Snapshot figé au moment de l'entrée dans l'écran
    val grid = remember { viewModel.getHexColors() }

    val scoreResult = remember(heights, laurels) {
        ScoreCalculator.calculate(
            grid = grid,
            heights = heights,
            laurels = laurels,
            neighborsOf = hexGridState::hexNeighbors
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text("←", color = Color.White, fontSize = 22.sp)
            }
            Text(
                "Calcul du score",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Lauriers par district",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            scoreResult.colorScores.forEach { colorScore ->
                DistrictRow(
                    colorScore = colorScore,
                    onDecrement = { viewModel.setLaurels(colorScore.district, colorScore.laurels - 1) },
                    onIncrement = { viewModel.setLaurels(colorScore.district, colorScore.laurels + 1) }
                )
                Spacer(Modifier.height(8.dp))
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF16213E), RoundedCornerShape(12.dp))
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Score total", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${scoreResult.total}",
                    color = Color(0xFFFFC107),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Retour à la caméra")
        }
    }
}

@Composable
private fun DistrictRow(
    colorScore: ColorScore,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF16213E), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(colorScore.district.argb))
            )
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(colorScore.district.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Groupe: ${colorScore.largestGroupValue} pt${if (colorScore.largestGroupValue > 1) "s" else ""}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrement, modifier = Modifier.size(32.dp)) {
                Text("−", color = Color.White, fontSize = 20.sp)
            }
            Text(
                "${colorScore.laurels}",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = onIncrement, modifier = Modifier.size(32.dp)) {
                Text("+", color = Color.White, fontSize = 20.sp)
            }
        }

        Text(
            "= ${colorScore.score}",
            color = if (colorScore.score > 0) Color(0xFFFFC107) else Color.White.copy(alpha = 0.4f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
