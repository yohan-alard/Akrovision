package fr.yaltech.games.akrovision.ui.camera

import androidx.lifecycle.ViewModel
import fr.yaltech.games.akrovision.model.DistrictColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CameraViewModel : ViewModel() {

    private val _grid = MutableStateFlow<Array<Array<DistrictColor?>>>(emptyArray())
    val grid: StateFlow<Array<Array<DistrictColor?>>> = _grid.asStateFlow()

    fun updateGrid(grid: Array<Array<DistrictColor?>>) {
        _grid.value = grid
    }
}
