package de.jworuna.webrtc_l4s.webrtc

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import org.webrtc.NetworkChangeDetector
import org.webrtc.NetworkChangeDetector.ConnectionType
import org.webrtc.NetworkMonitorAutoDetect

class CustomNetworkChangeDetector(observer: NetworkChangeDetector.Observer, context: Context, private val allowedNetwork: Network)
    : NetworkMonitorAutoDetect(observer, context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun getActiveNetworkList(): List<NetworkChangeDetector.NetworkInformation> {
        val linkProperties = connectivityManager.getLinkProperties(allowedNetwork) ?: return emptyList()
        val networkCapabilites = connectivityManager.getNetworkCapabilities(allowedNetwork)
        val connectionType = when {
            networkCapabilites?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> ConnectionType.CONNECTION_5G
            networkCapabilites?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> ConnectionType.CONNECTION_WIFI
            else -> ConnectionType.CONNECTION_UNKNOWN
        }

        val networkInfo = NetworkChangeDetector.NetworkInformation(
            linkProperties.interfaceName ?: "custom_if",
            connectionType,
            ConnectionType.CONNECTION_NONE,
            allowedNetwork.networkHandle,
            getIPAddresses(linkProperties).toTypedArray()
        )
        return listOf(networkInfo)
    }

    override fun getCurrentConnectionType(): ConnectionType {
        val networkCapabilities = connectivityManager.getNetworkCapabilities(allowedNetwork)
        return if (networkCapabilities != null) {
            when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CONNECTION_5G
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.CONNECTION_WIFI
                else -> ConnectionType.CONNECTION_UNKNOWN
            }
        } else {
            ConnectionType.CONNECTION_NONE
        }
    }

    private fun getIPAddresses(linkProperties: LinkProperties): List<NetworkChangeDetector.IPAddress> {
        val ipAddresses = mutableListOf<NetworkChangeDetector.IPAddress>()
        for(linkAddress: LinkAddress in linkProperties.linkAddresses) {
            ipAddresses.add(NetworkChangeDetector.IPAddress(linkAddress.address.address))
        }

        return ipAddresses
    }
}