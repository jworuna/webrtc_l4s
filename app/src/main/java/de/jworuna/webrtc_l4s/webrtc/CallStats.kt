package de.jworuna.webrtc_l4s.webrtc

data class CallStats(
    val rttMs: Double?,
    val videoPacketLossPercent: Double?,
    val videoBitrateKbps: Double?,
    val audioPacketLossPercent: Double?,
    val audioBitrateKbps: Double?,
    val screamEct1: Double? = null,
    val screamEctCe: Double? = null,
    val screamCeInPercent: Double? = null,
    val connectivity: String = ""
)
