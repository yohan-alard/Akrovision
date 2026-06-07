package fr.yaltech.games.akrovision.ui.camera

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
fun CameraScreen(
    viewModel: CameraViewModel = viewModel(),
    onNavigateToScore: () -> Unit = {}
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    when {
        cameraPermission.status.isGranted ->
            CameraPreviewWithOverlay(viewModel = viewModel, onNavigateToScore = onNavigateToScore)
        cameraPermission.status.shouldShowRationale ->
            PermissionRationale { cameraPermission.launchPermissionRequest() }
        else -> cameraPermission.launchPermissionRequest()
    }
}

@Composable
private fun CameraPreviewWithOverlay(
    viewModel: CameraViewModel,
    onNavigateToScore: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val calibrationMode by viewModel.calibrationMode.collectAsStateWithLifecycle()
    val hexGridState by viewModel.hexGridState.collectAsStateWithLifecycle()
    val heights by viewModel.heights.collectAsStateWithLifecycle()
    val selectedCell by viewModel.selectedCell.collectAsStateWithLifecycle()
    val hexColors by viewModel.hexColors.collectAsStateWithLifecycle()
    val hexMultipliers by viewModel.hexMultipliers.collectAsStateWithLifecycle()
    val cellStars by viewModel.cellStars.collectAsStateWithLifecycle()

    val previewView = remember { PreviewView(context) }

    // ─── Cycle de vie caméra ──────────────────────────────────────────────────────────────
    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        var disposed = false

        ProcessCameraProvider.getInstance(context).addListener({
            if (disposed) return@addListener
            try {
                val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                val preview = Preview.Builder().build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, ColorAnalyzer(onResult = viewModel::updateAnalysis)) }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
                )
            } catch (e: Exception) {
                android.util.Log.e("AkroVision", "Liaison caméra échouée", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            executor.shutdown()
            try { ProcessCameraProvider.getInstance(context).get()?.unbindAll() }
            catch (_: Exception) {}
        }
    }
    // ─────────────────────────────────────────────────────────────────────────────────────

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        LaunchedEffect(screenW, screenH) {
            if (screenW > 0 && screenH > 0) viewModel.initHexGrid(screenW, screenH)
        }

        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        if (!calibrationMode) {
            HexOverlay(
                hexGridState = hexGridState,
                analysis = analysis,
                heights = heights,
                cellStars = cellStars,
                selectedCell = selectedCell,
                screenW = screenW,
                screenH = screenH,
                onGridStateChanged = viewModel::updateHexGrid,
                onCellTapped = viewModel::selectCell,
                onHexColorsComputed = viewModel::updateHexColors,
                onHexMultipliersComputed = viewModel::updateHexMultipliers
            )
        }

        if (calibrationMode) {
            CalibrationOverlay(analysis = analysis)
        }

        // Routing : tuile multiplicateur → StarPicker / tuile normale → HeightPicker
        val cell = selectedCell
        if (cell != null && !calibrationMode) {
            val (selRow, selCol) = cell
            val district = hexColors.getOrNull(selRow)?.getOrNull(selCol)
            if (hexMultipliers.getOrNull(selRow)?.getOrNull(selCol) == true) {
                StarPicker(
                    currentStars = cellStars[selRow to selCol] ?: 1,
                    district = district,
                    onSelect = { stars -> viewModel.setCellStars(selRow, selCol, stars) },
                    onDismiss = viewModel::clearSelection,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            } else {
                HeightPicker(
                    district = district,
                    currentHeight = viewModel.heightAt(selRow, selCol),
                    onSelect = { h -> viewModel.setHeight(selRow, selCol, h, hexColors) },
                    onDismiss = viewModel::clearSelection,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        Button(
            onClick = viewModel::toggleCalibration,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
        ) {
            Text(if (calibrationMode) "Détection" else "Calibrer")
        }

        val hasDetection = hexColors.isNotEmpty() && hexColors.any { row -> row.any { it != null } }
        if (hasDetection && !calibrationMode && selectedCell == null) {
            Button(
                onClick = {
                    viewModel.snapshotAndInitLaurels(hexColors)
                    onNavigateToScore()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            ) {
                Text("Calculer le score →")
            }
        }

        if (!hasDetection && !calibrationMode && hexGridState.hexRadius > 0f) {
            Text(
                text = "Glissez / pincez pour aligner la grille sur les tuiles",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun StarPicker(
    currentStars: Int,
    district: DistrictColor?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val title = if (district?.givesPoints == true) "Multiplicateur — ${district.label}" else "Multiplicateur de score"
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            "Nombre d'étoiles sur la tuile",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(1, 2, 3).forEach { stars ->
                if (stars == currentStars) {
                    Button(
                        onClick = { onSelect(stars) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
                    ) {
                        Text("×$stars", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                } else {
                    OutlinedButton(onClick = { onSelect(stars) }) {
                        Text("×$stars", color = Color.White, fontSize = 18.sp)
                    }
                }
            }
        }

        OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) {
            Text("Annuler", color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun HeightPicker(
    district: DistrictColor?,
    currentHeight: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val title = if (district?.givesPoints == true) "Hauteur — ${district.label}" else "Hauteur de la pile"
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(1, 2, 3).forEach { level ->
                if (level == currentHeight) {
                    Button(
                        onClick = { onSelect(level) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107))
                    ) {
                        Text("$level", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                } else {
                    OutlinedButton(onClick = { onSelect(level) }) {
                        Text("$level", color = Color.White, fontSize = 18.sp)
                    }
                }
            }
        }

        OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) {
            Text("Annuler", color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun CalibrationOverlay(analysis: AnalysisResult?) {
    Box(Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val r = 40.dp.toPx()
            drawCircle(color = Color.White, radius = r, center = Offset(cx, cy), style = Stroke(2.dp.toPx()))
            drawLine(Color.White, Offset(cx - r * 1.3f, cy), Offset(cx + r * 1.3f, cy), 1.5.dp.toPx())
            drawLine(Color.White, Offset(cx, cy - r * 1.3f), Offset(cx, cy + r * 1.3f), 1.5.dp.toPx())
        }

        val hsv = analysis?.centerHsv
        val district = analysis?.centerDistrict

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(16.dp)
        ) {
            Text("Pointez le viseur sur une tuile", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            if (hsv != null) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    HsvChip("H", "%.0f°".format(hsv[0]))
                    HsvChip("S", "%.2f".format(hsv[1]))
                    HsvChip("V", "%.2f".format(hsv[2]))
                }
                val ok = district != null
                val districtLabel = district?.label ?: ""
                Text(
                    text = if (ok) "→ $districtLabel ✓" else "→ Non détecté ✗",
                    color = if (ok) Color(0xFF4CAF50) else Color(0xFFF44336),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (!ok) Text(
                    "Ajustez les plages dans DistrictColor.kt",
                    color = Color.Yellow,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
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
            Text(
                "La caméra est nécessaire pour détecter les tuiles.",
                color = Color.White,
                modifier = Modifier.padding(16.dp)
            )
            Button(onClick = onRequest) { Text("Autoriser la caméra") }
        }
    }
}
