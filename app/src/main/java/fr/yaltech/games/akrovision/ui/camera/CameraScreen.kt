package fr.yaltech.games.akrovision.ui.camera

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import fr.yaltech.games.akrovision.detection.ColorAnalyzer
import fr.yaltech.games.akrovision.model.DistrictColor
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(viewModel: CameraViewModel = viewModel()) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val grid by viewModel.grid.collectAsStateWithLifecycle()

    when {
        cameraPermission.status.isGranted -> {
            CameraPreviewWithOverlay(
                grid = grid,
                onGridDetected = viewModel::updateGrid
            )
        }
        cameraPermission.status.shouldShowRationale -> {
            PermissionRationale(onRequest = { cameraPermission.launchPermissionRequest() })
        }
        else -> {
            cameraPermission.launchPermissionRequest()
        }
    }
}

@Composable
private fun CameraPreviewWithOverlay(
    grid: Array<Array<DistrictColor?>>,
    onGridDetected: (Array<Array<DistrictColor?>>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val textMeasurer = rememberTextMeasurer()

    Box(Modifier.fillMaxSize()) {

        // Flux caméra
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val future = ProcessCameraProvider.getInstance(ctx)

                future.addListener({
                    val cameraProvider = future.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val analysis = ImageAnalysis.Builder()
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(executor, ColorAnalyzer(onResult = onGridDetected))
                        }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay de détection
        if (grid.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rows = grid.size
                val cols = grid[0].size
                val cellW = size.width / cols
                val cellH = size.height / rows

                for (row in grid.indices) {
                    for (col in grid[row].indices) {
                        val district = grid[row][col]
                        val left = col * cellW
                        val top = row * cellH

                        // Fond coloré semi-transparent si couleur détectée
                        if (district != null) {
                            drawRect(
                                color = Color(district.argb).copy(alpha = 0.35f),
                                topLeft = Offset(left, top),
                                size = Size(cellW, cellH)
                            )
                        }

                        // Grille de debug
                        drawRect(
                            color = Color.White.copy(alpha = 0.4f),
                            topLeft = Offset(left, top),
                            size = Size(cellW, cellH),
                            style = Stroke(width = 1.dp.toPx())
                        )

                        // Label de la couleur détectée
                        if (district != null) {
                            drawText(
                                textMeasurer = textMeasurer,
                                text = district.label,
                                topLeft = Offset(left + 4.dp.toPx(), top + 4.dp.toPx()),
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "La caméra est nécessaire pour détecter les tuiles.",
                color = Color.White,
                modifier = Modifier.padding(16.dp)
            )
            Button(onClick = onRequest) {
                Text("Autoriser la caméra")
            }
        }
    }
}
