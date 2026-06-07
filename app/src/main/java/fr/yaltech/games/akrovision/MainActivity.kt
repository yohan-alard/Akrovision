package fr.yaltech.games.akrovision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.yaltech.games.akrovision.ui.camera.CameraScreen
import fr.yaltech.games.akrovision.ui.camera.CameraViewModel
import fr.yaltech.games.akrovision.ui.score.ScoreScreen
import fr.yaltech.games.akrovision.ui.theme.AkroVisionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AkroVisionTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val sharedViewModel: CameraViewModel = viewModel()

                    NavHost(navController = navController, startDestination = "camera") {
                        composable("camera") {
                            CameraScreen(
                                viewModel = sharedViewModel,
                                onNavigateToScore = { navController.navigate("score") }
                            )
                        }
                        composable("score") {
                            ScoreScreen(
                                viewModel = sharedViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
