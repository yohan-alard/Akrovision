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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
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
import fr.yaltech.games.akrovision.model.AnalysisResult
import fr.yaltech.games.akrovision.model.DistrictColor
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(viewModel: CameraViewModel = viewModel()) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val result by viewModel.result.collectAsStateWithLifecycle()
    val calibrationMode by viewModel.calibrationMode.collectAsStateWithLifecycle()

    when {
        cameraPermission.status.isGranted -> {
            CameraPreviewWithOverlay(
                result = result,
                calibrationMode = calibrationMode,
                onResult = viewModel::updateResult,
                onToggleCalibration = viewModel::toggleCalibration
            )
        }
        cameraPermission.status.shouldShowRationale -> {
            PermissionRationale(onRequest = { cameraPermission.launchPermissionRequest() })
        }
        else -> cameraPermission.launchPermissionRequest()
    }
}

@Composable
private fun CameraPreviewWithOverlay(
    result: AnalysisResult?,
    calibrationMode: Boolean,
    onResult: (AnalysisResult) -> Unit,
    onToggleCalibration: () -> Unit
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
                ProcessCameraProvider.getInstance(ctx).addListener({
                    val cameraProvider = ProcessCameraProvider.getInstance(ctx).get()
                    val preview = Preview.Builder().build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(executor, ColorAnalyzer(onResult = onResult)) }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay grille de détection
        val grid = result?.grid
        if (grid != null && !calibrationMode) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rows = grid.size
                val cols = if (rows > 0) grid[0].size else return@Canvas
                val cellW = size.width / cols
                val cellH = size.height / rows

                for (row in grid.indices) {
                    for (col in grid[row].indices) {
                        val district = grid[row][col]
                        val left = col * cellW
                        val top = row * cellH

                        if (district != null) {
                            // Pierre : fond discret, pas de label (pas de points)
                            val alpha = if (district.givesPoints) 0.35f else 0.15f
                            drawRect(
                                color = Color(district.argb).copy(alpha = alpha),
                                topLeft = Offset(left, top),
                                size = Size(cellW, cellH)
                            )
                            if (district.givesPoints) {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = district.label,
                                    topLeft = Offset(left + 4.dp.toPx(), top + 4.dp.toPx()),
                                    style = TextStyle(color = Color.White, fontSize = 10.sp)
                                )
                            }
                        }
                        drawRect(
                            color = Color.White.copy(alpha = 0.3f),
                            topLeft = Offset(left, top),
                            size = Size(cellW, cellH),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
            }
        }

        // Overlay calibration : viseur central + panneau HSV
        if (calibrationMode) {
            CalibrationOverlay(result = result, textMeasurer = textMeasurer)
        }

        // Bouton bascule calibration/detection
        Button(
            onClick = onToggleCalibration,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
        ) {
            Text(if (calibrationMode) "Détection" else "Calibrer")
        }
    }
}

@Composable
private fun CalibrationOverlay(
    result: AnalysisResult?,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    Box(Modifier.fillMaxSize()) {

        // Viseur central
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val r = 40.dp.toPx()

            // Cercle du viseur
            drawCircle(color = Color.White, radius = r, center = Offset(cx, cy), style = Stroke(2.dp.toPx()))
            // Croix
            drawLine(Color.White, Offset(cx - r * 1.3f, cy), Offset(cx + r * 1.3f, cy), 1.5.dp.toPx())
            drawLine(Color.White, Offset(cx, cy - r * 1.3f), Offset(cx, cy + r * 1.3f), 1.5.dp.toPx())
        }

        // Panneau HSV en bas
        val hsv = result?.centerHsv
        val district = result?.centerDistrict

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "Calibration — pointez le viseur sur une tuile",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )

            if (hsv != null) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    HsvChip("H", "%.0f°".format(hsv[0]))
                    HsvChip("S", "%.2f".format(hsv[1]))
                    HsvChip("V", "%.2f".format(hsv[2]))
                }

                val statusColor = if (district != null) Color(0xFF4CAF50) else Color(0xFFF44336)
                val statusText = if (district != null) "→ ${district.label} ✓" else "→ Non détecté ✗"

                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )

                if (district == null && hsv[0] > 0f) {
                    Text(
                        text = "Ajustez les plages HSV dans DistrictColor.kt",
                        color = Color.Yellow,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HsvChip(label: String, value: String) {
    Column(
        modifier = Modifier
            .padding(end = 16.dp)
            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("La caméra est nécessaire pour détecter les tuiles.", color = Color.White, modifier = Modifier.padding(16.dp))
            Button(onClick = onRequest) { Text("Autoriser la caméra") }
        }
    }
}
