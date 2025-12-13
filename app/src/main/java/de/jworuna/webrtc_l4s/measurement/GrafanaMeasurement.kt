package de.jworuna.webrtc_l4s.measurement

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import de.jworuna.webrtc_l4s.cellinfo.CurrentCellInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class GrafanaMeasurement : IMeasurementCallback
{
    private lateinit var basicAuth: String
    private val TAG = "GrafanaMeasurement"
    private lateinit var logUrl: URL
    private val gson = Gson()

    override fun setLogUrlAndCredentials(url: String, username: String, password: String) {
        logUrl = URL(url)
        basicAuth = encodeAuth(username, password)
    }

    override fun close() {
    }

    private suspend fun sendToApi(data: List<MeasurementItem>): Int = withContext(Dispatchers.IO)
    {
        val connection = logUrl.openConnection() as HttpsURLConnection
        val measurementItemList = mutableListOf<MeasurementItem>()
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", basicAuth)

            for (item in data)
            {
                measurementItemList.add(MeasurementItem(
                    timeStampMs = item.timeStampMs,
                    rttMs = item.rttMs,
                    loadkbits = item.loadkbits,
                    ecnCePercent = item.ecnCePercent,
                    packetLossCount = item.packetLossCount,
                    cellId = CurrentCellInfo.cellId,
                    pci = CurrentCellInfo.pci,
                    band = CurrentCellInfo.band,
                    streamId = item.streamId,
                    sessionName = item.sessionName,
                    isNrSa = CurrentCellInfo.isNrSa,
                    dbm = item.dbm,
                    lat = item.lat,
                    lon = item.lon
                ))
            }

            val jsonData = gson.toJson(measurementItemList)
            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(jsonData)
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode
            return@withContext responseCode
        } catch (e: Exception) {
            e.message?.let { Log.e(TAG, it) }
        } finally {
            connection.disconnect()
            measurementItemList.clear()
        }!!
    }

    private fun encodeAuth(username: String, password: String): String {
        val auth = "$username:$password"
        return "Basic " + Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP)
    }

    override fun onMeasurementItemCallback(logItems: Array<MeasurementItem>) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = sendToApi(logItems.toList())
            if (result != 204)
                Log.d(TAG, "Error server response: $result")
        }
    }

    override fun showSummary() {
    }
}