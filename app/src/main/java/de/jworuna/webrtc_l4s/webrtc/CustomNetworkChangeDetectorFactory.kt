package de.jworuna.webrtc_l4s.webrtc

import android.content.Context
import android.net.Network
import org.webrtc.NetworkChangeDetector
import org.webrtc.NetworkChangeDetectorFactory

class CustomNetworkChangeDetectorFactory(
    private val allowedNetwork: Network
) : NetworkChangeDetectorFactory {
    override fun create(observer: NetworkChangeDetector.Observer, context: Context): NetworkChangeDetector {
        return CustomNetworkChangeDetector(observer, context, allowedNetwork)
    }
}