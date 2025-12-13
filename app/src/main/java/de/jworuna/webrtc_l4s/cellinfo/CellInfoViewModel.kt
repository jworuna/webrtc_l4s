package de.jworuna.webrtc_l4s.cellinfo

import android.content.Context
import androidx.lifecycle.LiveData

class CellInfoProvider(context: Context) {
    private val appContext = context.applicationContext
    private val locationProvider = LocationProvider(appContext)
    private val cellInfoLiveData = CellInfoLiveData(appContext, locationProvider)

    val currentCellInfo: LiveData<CurrentCellInfo>
        get() = cellInfoLiveData

    fun setLocationEnabled(enabled: Boolean) {
        cellInfoLiveData.setLocationEnabled(enabled)
    }

    fun clear() {
        cellInfoLiveData.clear()
    }
}
