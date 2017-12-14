package com.intellij.stats.personalization.impl

/**
 * @author Vitaliy.Bibaev
 */
object FactorsUtil {
    fun mergeAverage(n1: Int, avg1: Double, n2: Int, avg2: Double): Double {
        if (n1 == 0 && n2 == 0) return 0.0
        val total = (n1 + n2).toDouble()
        return (n1 / total) * avg1 + (n2 / total) * avg2
    }

    fun updateAverageValue(map: MutableMap<String, Double>, valueToAdd: Double) {
        val count = map["count"]?.toInt()
        val avg = map["average"]
        if (count != null && avg != null) {
            val newAverage = mergeAverage(1, valueToAdd, count, avg)
            update(map, 1 + count, newAverage)
        } else {
            update(map, 1, valueToAdd)
        }
    }

    fun calculateAverageByAllDays(factor: DailyAggregatedDoubleFactor): Double? {
        var totalCount = 0
        var average = 0.0
        var present = false
        for (onDate in factor.availableDays().mapNotNull { factor.onDate(it) }) {
            val avg = onDate["average"]
            val count = onDate["count"]?.toInt()
            if (avg != null && count != null && count > 0) {
                present = true
                average = FactorsUtil.mergeAverage(totalCount, average, count, avg)
                totalCount += count
            }
        }

        return if (present) average else average
    }


    private fun update(map: MutableMap<String, Double>, count: Int, avg: Double) {
        map["count"] = count.toDouble()
        map["average"] = avg
    }
}