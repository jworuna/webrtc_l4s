package de.jworuna.webrtc_l4s.measurement

interface IMeasurementCallback
{
    fun onMeasurementItemCallback(logItems: Array<MeasurementItem>)
    fun showSummary()
    fun setLogUrlAndCredentials(url: String, username: String, password: String)
    fun close()
}