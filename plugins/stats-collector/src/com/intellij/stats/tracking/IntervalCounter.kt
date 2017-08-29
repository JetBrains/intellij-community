package com.intellij.stats.tracking

data class IntervalData(val intervalStart: Double, val intervalEnd: Double, val count: Int)

class IntervalCounter(
        private val minPower: Int,
        private val maxPower: Int,
        private val exponent: Double
) {

    private val values = Array(maxPower - minPower, { 0 })

    fun register(value: Long) {
        val log = roundedLog(value)
        val bucket = Math.min(maxPower - 1, Math.max(minPower, log.toInt())) - minPower
        values[bucket] += 1
    }

    fun data(): List<IntervalData> {
        return values.indices.map { interval(it) }
    }

    private fun interval(index: Int): IntervalData {
        val start = Math.pow(exponent, index.toDouble())
        val end = Math.pow(exponent, (index + 1).toDouble())

        val count = values[index]

        return IntervalData(start, end, count)
    }

    private fun roundedLog(time: Long): Double {
        val dTime = time.toDouble()
        return (Math.log(dTime) / Math.log(exponent))
    }

}