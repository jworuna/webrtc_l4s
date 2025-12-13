package de.jworuna.webrtc_l4s.cellinfo

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log

fun interface WifiChangeCallback {
    fun onChange(isWifi: Boolean)
}

class WifiChangeListener(
    context: Context,
    callback: WifiChangeCallback) {
    private val TAG = "WifiChangeListener"

    private val _context = context
    private var wifiNetworkCallback: ConnectivityManager.NetworkCallback? = null
    var wifiCallback: WifiChangeCallback = callback

    fun registerWifiChangeListener()
    {
        Log.d(TAG, "registerWifiChangeListener")
        val connectivityManager = _context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        wifiNetworkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    wifiCallback.onChange(true)
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    Log.d(TAG, "Connected to WiFi Lost")
                    wifiCallback.onChange(false)
                }
            }
        }

        connectivityManager.registerDefaultNetworkCallback(wifiNetworkCallback!!)
    }

    fun unRegisterWifiChangeListener()
    {
        val connectivityManager = _context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiNetworkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
            wifiNetworkCallback = null
        }
    }

    fun isOnWifi(): Boolean {
        val connectivityManager = _context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}