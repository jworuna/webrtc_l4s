package de.jworuna.webrtc_l4s.cellinfo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

object CurrentCellInfo {
    var isNrSa: Int = 0
    var cellId: Long = 0
    var pci: Int = 0
    var band: Int = 0
    var dbm: Int = 0
    var lat: Double? = null
    var lon: Double? = null
}

class CellInfoLiveData(
    context: Context,
    private val locationProvider: LocationProvider,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : LiveData<CurrentCellInfo>()
{
    private val TAG = "CellInfoLiveData"

    private val _context = context
    private val _signalStrengthExecutor = Executors.newSingleThreadExecutor()
    private val _telephonyManager = _context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var wifiNetworkCallback: ConnectivityManager.NetworkCallback? = null

    private var _repeatSignalStrengthRequest: Job? = null

    private var _lastRadioAccessTechnology: String = "Undefined"
    private var _lastCellId: Long = 0
    private var _lastPci: Int = 0
    private var _lastTac: Int = 0
    private var _lastBand: String = "Unknown"
    private var _lastBandFreq: Int = 0
    private var _lastIsNrSa: Int = 0
    private var _lastDbm: Int = 0
    private var locationJob: Job? = null
    private var locationEnabled: Boolean = true

    private val signalStrengthCallback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener, TelephonyCallback.CellInfoListener
    {
        @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
        override fun onSignalStrengthsChanged(signalStrength: android.telephony.SignalStrength) {
            for (item in signalStrength.cellSignalStrengths) {
                when (item) {
                    is CellSignalStrengthLte -> {
                        _lastDbm = item.dbm
                    }

                    is CellSignalStrengthNr -> {
                        _lastDbm = item.dbm
                    }
                }
            }
        }

        override fun onCellInfoChanged(p0: List<CellInfo?>) {
            getRadioAccess(p0)
        }

        private fun getRadioAccess(cellInfo: List<CellInfo?>)
        {
            for (cell in cellInfo)
            {
                if (cell?.isRegistered == true)
                {
                    when (cell)
                    {
                        is CellInfoGsm -> _lastRadioAccessTechnology = "Gsm"
                        is CellInfoWcdma -> _lastRadioAccessTechnology = "WCdma"
                        is CellInfoTdscdma -> _lastRadioAccessTechnology = "TdsCdma"
                        is CellInfoLte -> _lastRadioAccessTechnology = "LTE"
                        is CellInfoNr -> _lastRadioAccessTechnology = "NR"
                    }

                    when (val identity = cell.cellIdentity)
                    {
                        is CellIdentityLte -> {
                            _lastCellId = identity.ci.toLong()
                            _lastTac = identity.tac
                            _lastPci = identity.pci
                            _lastBand = getLteBandAndFrequency(identity.earfcn)
                            _lastIsNrSa = 0
                        }
                        is CellIdentityNr -> {
                            _lastCellId = identity.nci
                            _lastTac = identity.tac
                            _lastPci = identity.pci
                            _lastIsNrSa = 1
                            when (identity.nrarfcn)
                            {
                                in 410000..440000 -> {
                                    _lastBand = "n1 (2100 MHz)"
                                    _lastBandFreq = 2100
                                }
                                in 620000..680000 -> {
                                    _lastBand = "n78 (3600 MHz)"
                                    _lastBandFreq = 3600
                                }
                                in 151000..160600 -> {
                                    _lastBand = "n28 (700 MHz)"
                                    _lastBandFreq = 700
                                }
                                else -> {
                                    _lastBand = "Unknown Band"
                                    _lastBandFreq = 0
                                }
                            }
                        }
                    }

                    postData()
                    break
                }
            }
        }
    }


    @SuppressLint("MissingPermission")
    override fun onActive() {
        Log.d(TAG, "CellInfoLiveData -> onActive")
        super.onActive()
        registerWifiChangeListener(_context)
        if (hasLocationPermission() && !isOnWifi(_context)) {
            _telephonyManager.registerTelephonyCallback(_signalStrengthExecutor, signalStrengthCallback)
            startLocationUpdates()
        } else {
            Log.w(TAG, "Location permission missing; skipping telephony callbacks")
        }
    }

    override fun onInactive() {
        Log.d(TAG, "CellInfoLiveData -> onInactive")
        super.onInactive()
        unregisterWifiChangeListener(_context)
        runCatching { _telephonyManager.unregisterTelephonyCallback(signalStrengthCallback) }
        stopLocationUpdates()
    }

    fun isOnWifi(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    @SuppressLint("MissingPermission")
    private fun postData()
    {
        CurrentCellInfo.cellId = _lastCellId
        CurrentCellInfo.pci = _lastPci
        CurrentCellInfo.band = _lastBandFreq
        CurrentCellInfo.isNrSa = _lastIsNrSa
        CurrentCellInfo.dbm = _lastDbm

        postValue(CurrentCellInfo)
    }

    fun registerWifiChangeListener(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    Log.d(TAG, "Connected to WiFi")

                    if (_repeatSignalStrengthRequest != null && _repeatSignalStrengthRequest?.isActive == true)
                    {
                        _repeatSignalStrengthRequest?.cancel()
                        _repeatSignalStrengthRequest = null
                    }

                    _lastRadioAccessTechnology = "Wifi"
                    _lastCellId = 0
                    _lastPci = 0
                    _lastTac = 0
                    _lastBand = ""
                    _lastBandFreq = 0
                    _lastIsNrSa = 0
                    postData()
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.d(TAG, "Connected to WiFi Lost")
            }
        }

        wifiNetworkCallback = callback
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    fun unregisterWifiChangeListener(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiNetworkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
            wifiNetworkCallback = null
        }
    }

    private fun startLocationUpdates() {
        if (locationJob?.isActive == true) return
        if (!locationEnabled || !hasLocationPermission()) return
        locationJob = scope.launch {
            locationProvider.getContinuousUpdates().collectLatest { loc ->
                CurrentCellInfo.lat = loc.latitude
                CurrentCellInfo.lon = loc.longitude
                postValue(CurrentCellInfo)
            }
        }
    }

    private fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
    }

    fun clear() {
        stopLocationUpdates()
        scope.cancel()
    }

    fun setLocationEnabled(enabled: Boolean) {
        locationEnabled = enabled
        if (!enabled) {
            stopLocationUpdates()
        } else if (hasLocationPermission()) {
            startLocationUpdates()
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(_context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(_context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun getLteBandAndFrequency(arfcn: Int) : String
    {
        var result: String
        when (arfcn) {
            in 0..599 -> {
                result = "B1 (2100 MHz)"
                _lastBandFreq = 2100
            }
            in 600..1199 -> {
                result = "B2 (1900 MHz)"
                _lastBandFreq = 1900
            }
            in 1200..1949 -> {
                result = "B3 (1800 MHz)"
                _lastBandFreq = 1800
            }
            in 1950..2399 -> {
                result = "B4 (1700/2100 MHz AWS)"
                _lastBandFreq = 1700
            }
            in 2400..2649 -> {
                result = "B7 (2600 MHz)"
                _lastBandFreq = 2600
            }
            in 2750..3449 -> {
                result = "B8 (900 MHz)"
                _lastBandFreq = 900
            }
            in 3450..3799 -> {
                result = "B9 (1800 MHz)"
                _lastBandFreq = 1800
            }
            in 3800..4149 -> {
                result = "B10 (1700/2100 MHz)"
                _lastBandFreq = 1700
            }
            in 4150..4749 -> {
                result = "B11 (1500 MHz)"
                _lastBandFreq = 1500
            }
            in 4750..4999 -> {
                result = "B12 (700 MHz)"
                _lastBandFreq = 700
            }
            in 5000..5179 -> {
                result = "B13 (700 MHz)"
                _lastBandFreq = 700
            }
            in 5180..5279 -> {
                result = "B14 (700 MHz FirstNet)"
                _lastBandFreq = 700
            }
            in 5280..5379 -> {
                result = "B17 (700 MHz)"
                _lastBandFreq = 700
            }
            in 5730..5849 -> {
                result = "B18 (850 MHz)"
                _lastBandFreq = 850
            }
            in 5850..5999 -> {
                result = "B19 (850 MHz)"
                _lastBandFreq = 850
            }
            in 6000..6149 -> {
                result = "B20 (800 MHz)"
                _lastBandFreq = 800
            }
            in 6150..6449 -> {
                result = "B20 (800 MHz)"
                _lastBandFreq = 800
            }
            in 6450..6599 -> {
                result = "B21 (1500 MHz)"
                _lastBandFreq = 1500
            }
            in 6600..7399 -> {
                result = "B22 (3500 MHz)"
                _lastBandFreq = 3500
            }
            in 7500..7699 -> {
                result = "B25 (1900 MHz extended)"
                _lastBandFreq = 1900
            }
            in 7700..8039 -> {
                result = "B26 (850 MHz extended)"
                _lastBandFreq = 850
            }
            in 8040..8689 -> {
                result = "B27 (800 MHz)"
                _lastBandFreq = 800
            }
            in 8690..9039 -> {
                result = "B28 (700 MHz)"
                _lastBandFreq = 700
            }
            in 9040..9209 -> {
                result = "B29 (700 MHz Supplemental)"
                _lastBandFreq = 700
            }
            in 9210..9659 -> {
                result = "B28 (700 MHz)"
                _lastBandFreq = 700
            }
            in 9660..9769 -> {
                result = "B30 (2300 MHz)"
                _lastBandFreq = 2300
            }
            in 9770..9869 -> {
                result = "B31 (450 MHz)"
                _lastBandFreq = 450
            }
            in 9870..9919 -> {
                result = "B32 (1500 MHz SDL)"
                _lastBandFreq = 1500
            }
            in 9920..10359 -> {
                result = "B33 (1900 MHz TDD)"
                _lastBandFreq = 1900
            }
            in 36000..36199 -> {
                result = "B65 (AWS-1 + 3G)"
                _lastBandFreq = 0
            }
            in 46000..46589 -> {
                result = "B66 (AWS-3)"
                _lastBandFreq = 0
            }
            else -> {
                result = "Unknown Band"
                _lastBandFreq = 0
            }
        }

        return result
    }
}
