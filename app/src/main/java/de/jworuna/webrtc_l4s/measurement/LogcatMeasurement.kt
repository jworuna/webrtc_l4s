package de.jworuna.webrtc_l4s.measurement

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.pow

class LogcatMeasurement : IMeasurementCallback
{
    private val TAG = "LogcatMeasurement"
    private var logItemList: MutableList<MeasurementItem> = emptyList<MeasurementItem>().toMutableList()

    init
    {
        logItemList.clear()
    }

    override fun onMeasurementItemCallback(logItems: Array<MeasurementItem>) {
        CoroutineScope(Dispatchers.IO).launch {
            for (item in logItems) {
                Log.d(TAG, "[${item.timeStampMs}]: ${item.loadkbits}kbps, " +
                        "${item.rttMs}ms, ${item.ecnCePercent}%, ${item.packetLossCount}")
            }

            logItemList.addAll(logItems)
        }
    }

    override fun showSummary()
    {
        if (logItemList.isNotEmpty())
        {
            val rttList = logItemList.map { it.rttMs }.sorted()

            val hasCeMarks = logItemList.any { it.ecnCePercent > 0 }
            val lossCount = logItemList.sumOf { it.packetLossCount }
            val average = rttList.average().round(2)
            val p95 = percentile(rttList, 95.0).round(2)
            val p99 = percentile(rttList, 99.0).round(2)
            val p9995 = percentile(rttList, 99.95).round(2)
            Log.d(TAG, "Summary -> HasCe: ${hasCeMarks}, lossCount: ${lossCount}, Average: ${average}ms, p95: ${p95}ms, p99: ${p99}ms, p99,95: ${p9995}ms")
            logItemList.clear()
        }
    }

    override fun setLogUrlAndCredentials(url: String, username: String, password: String) {
    }

    override fun close() {
    }

    private fun percentile(sortedList: List<Double>, percentile: Double): Double {
        if (sortedList.isEmpty()) return Double.NaN
        val n = sortedList.size
        val rank = percentile / 100.0 * (n - 1)
        val lower = rank.toInt()
        val upper = lower + 1
        val weight = rank - lower

        return if (upper < n) {
            sortedList[lower] * (1 - weight) + sortedList[upper] * weight
        } else {
            sortedList[lower]
        }
    }

    private fun Double.round(decimals: Int): Double {
        val factor = 10.0.pow(decimals)
        return kotlin.math.round(this * factor) / factor
    }
}