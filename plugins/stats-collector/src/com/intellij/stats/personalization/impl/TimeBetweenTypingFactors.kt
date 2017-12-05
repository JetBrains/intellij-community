package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.*

/**
 * @author Vitaliy.Bibaev
 */
class TimeBetweenTypingReader(factor: DailyAggregatedDoubleFactor) : UserFactorReaderBase(factor) {
    fun averageTime(): Double? {
        var totalCount = 0
        var average = 0.0
        for (onDate in factor.availableDates().mapNotNull { factor.onDate(it) }) {
            val avg = onDate["average"]
            val count = onDate["count"]?.toInt()
            if (avg != null && count != null && count > 0) {
                average = mergeAverage(totalCount, average, count, avg)
                totalCount += count
            }
        }

        return average
    }
}

class TimeBetweenTypingUpdater(factor: MutableDoubleFactor) : UserFactorUpdaterBase(factor) {
    fun fireTypingPerformed(delayMs: Int) {
        factor.updateOnDate(DateUtil.today()) {
            val count = get("count")?.toInt()
            val avg = get("average")
            if (count != null && avg != null) {
                val newAverage = mergeAverage(1, delayMs.toDouble(), count, avg)
                update(this, 1 + count, newAverage)
            } else {
                update(this, 1, delayMs.toDouble())
            }
        }
    }

    private fun update(map: MutableMap<String, Double>, count: Int, avg: Double) {
        map["count"] = count.toDouble()
        map["average"] = avg
    }
}

private fun mergeAverage(n1: Int, avg1: Double, n2: Int, avg2: Double): Double {
    if (n1 == 0 && n2 == 0) return 0.0
    val total = (n1 + n2).toDouble()
    return (n1 / total) * avg1 + (n2 / total) * avg2
}

class AverageTimeBetweenTyping
    : UserFactorBase<TimeBetweenTypingReader>("averageTimeBetweenTyping", UserFactorDescriptions.TIME_BETWEEN_TYPING) {
    override fun compute(reader: TimeBetweenTypingReader): String? = reader.averageTime()?.toString()
}