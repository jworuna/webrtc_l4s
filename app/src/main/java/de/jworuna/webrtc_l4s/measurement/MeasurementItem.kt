package de.jworuna.webrtc_l4s.measurement

class MeasurementItem(
    val timeStampMs: Long,
    val rttMs: Double,
    val loadkbits: Long,
    val ecnCePercent: Double,
    var packetLossCount: Int,
    var cellId: Long,
    var pci: Int,
    var band: Int,
    var streamId: String,
    var sessionName: String,
    var isNrSa: Int,
    var dbm: Int?,
    var lat: Double?,
    var lon: Double?
)