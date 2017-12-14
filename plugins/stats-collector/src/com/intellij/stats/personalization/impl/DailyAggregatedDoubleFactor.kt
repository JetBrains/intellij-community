package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.DateUtil
import com.intellij.stats.personalization.Day


/**
 * @author Vitaliy.Bibaev
 */
interface DailyAggregatedDoubleFactor {
    fun availableDays(): List<Day>

    fun onDate(date: Day): Map<String, Double>?
}

interface MutableDoubleFactor : DailyAggregatedDoubleFactor {
    fun incrementOnToday(key: String): Boolean

    fun updateOnDate(date: Day, updater: MutableMap<String, Double>.() -> Unit): Boolean
}

private fun DailyAggregatedDoubleFactor.aggregateBy(reduce: (Double, Double) -> Double): Map<String, Double> {
    val result = mutableMapOf<String, Double>()
    for (onDate in availableDays().mapNotNull(this::onDate)) {
        for ((key, value) in onDate) {
            result.compute(key, { _, old -> if (old == null) value else reduce(old, value) })
        }
    }

    return result
}

fun MutableDoubleFactor.setOnDate(date: Day, key: String, value: Double): Boolean =
        updateOnDate(date) { put(key, value) }

fun DailyAggregatedDoubleFactor.onToday(): Map<String, Double> = onDate(DateUtil.today()) ?: emptyMap()

fun DailyAggregatedDoubleFactor.aggregateMin(): Map<String, Double> = aggregateBy(::minOf)

fun DailyAggregatedDoubleFactor.aggregateMax(): Map<String, Double> = aggregateBy(::maxOf)

fun DailyAggregatedDoubleFactor.aggregateSum(): Map<String, Double> = aggregateBy({ d1, d2 -> d1 + d2 })

fun DailyAggregatedDoubleFactor.aggregateAverage(): Map<String, Double> {
    val result = mutableMapOf<String, Double>()
    val counts = mutableMapOf<String, Int>()
    for (onDate in availableDays().mapNotNull(this::onDate)) {
        for ((key, value) in onDate) {
            result.compute(key) { _, old ->
                if (old != null) {
                    val n = counts[key]!!.toDouble()
                    counts.computeIfPresent(key) { _, value -> value + 1 }
                    FactorsUtil.mergeAverage(n.toInt(), old, 1, value)
                } else {
                    counts[key] = 1
                    value
                }
            }
        }
    }

    return result
}
