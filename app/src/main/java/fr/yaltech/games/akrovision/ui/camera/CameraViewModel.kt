package fr.yaltech.games.akrovision.ui.camera

import androidx.lifecycle.ViewModel
import fr.yaltech.games.akrovision.model.AnalysisResult
import fr.yaltech.games.akrovision.model.DistrictColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CameraViewModel : ViewModel() {

    private val _result = MutableStateFlow<AnalysisResult?>(null)
    val result: StateFlow<AnalysisResult?> = _result.asStateFlow()

    private val _calibrationMode = MutableStateFlow(false)
    val calibrationMode: StateFlow<Boolean> = _calibrationMode.asStateFlow()

    fun updateResult(result: AnalysisResult) {
        _result.value = result
    }

    fun toggleCalibration() {
        _calibrationMode.value = !_calibrationMode.value
    }
}
