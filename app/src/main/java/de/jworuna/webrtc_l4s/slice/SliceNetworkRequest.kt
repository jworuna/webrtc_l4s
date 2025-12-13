package de.jworuna.webrtc_l4s.slice

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection

object SliceNetwork {
    var network: Network? = null
    var ipv6: String? = null
    var ipv4: String? = null
    var sliceState: String = "Not requested"
    var sliceInterface: String = ""
}

class SliceNetworkRequest(context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun requestSlice(timeout: Int) {
        Log.d("SliceNetworkRequest", "requestSlice")
        val networkRequest = NetworkRequest
            .Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
            .build()

        connectivityManager.requestNetwork(
            networkRequest,
            sliceNetworkCallback,
            timeout
        )
    }

    fun releaseSlice()
    {
        Log.d("SliceNetworkRequest", "releaseSlice")
        connectivityManager.unregisterNetworkCallback(sliceNetworkCallback)
    }

    private val sliceNetworkCallback = object : ConnectivityManager.NetworkCallback()
    {
        override fun onAvailable(network: Network) {
            SliceNetwork.network = network
            SliceNetwork.sliceState = "Available"
            Log.d("SliceNetworkRequest", "onAvailable")
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            super.onLinkPropertiesChanged(network, linkProperties)
            Log.d("SliceNetworkRequest", "onLinkPropertiesChanged")

            for (item in linkProperties.linkAddresses)
                if (item.address.hostAddress != null)
                    if (isIPv6Address(item.address.hostAddress!!))
                        SliceNetwork.ipv6 = item.address.hostAddress!!

            SliceNetwork.sliceInterface = linkProperties.interfaceName!!
            CoroutineScope(Dispatchers.Main).launch {
                SliceNetwork.ipv4 = getMyPublicIpAsync(network).await()
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d("SliceNetworkRequest", "onLost")
            SliceNetwork.network = null
            SliceNetwork.ipv4 = null
            SliceNetwork.ipv6 = null
            SliceNetwork.sliceInterface = ""
            SliceNetwork.sliceState = "Not requested"
        }

        override fun onUnavailable() {
            super.onUnavailable()
            Log.d("SliceNetworkRequest", "onUnavailable")
            SliceNetwork.sliceState = "Unavailable"
            SliceNetwork.sliceInterface = ""
            SliceNetwork.ipv4 = null
            SliceNetwork.ipv6 = null
        }
    }

    fun isIPv6Address(ip: String): Boolean {
        return try {
            val inetAddress = InetAddress.getByName(ip)
            inetAddress is Inet6Address
        } catch (e: UnknownHostException) {
            false
        }
    }

    private suspend fun getMyPublicIpAsync(network: Network) : Deferred<String> =
        coroutineScope {
            async(Dispatchers.IO) {
                val result: String = try {
                    val url = URL("https://api.ipify.org")
                    val httpsURLConnection = network.openConnection(url) as HttpsURLConnection
                    val iStream = httpsURLConnection.getInputStream()
                    val buff = ByteArray(1024)
                    val read = iStream.read(buff)
                    String(buff,0, read)
                } catch (e: Exception) {
                    "error : $e"
                }
                return@async result
            }
        }
}