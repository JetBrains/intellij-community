package com.intellij.stats.personalization.impl

/**
 * @author Vitaliy.Bibaev
 */
interface DailyAggregatedDoubleFactor {
    fun availableDates(): List<String>

    fun onDate(date: String): Map<String, Double>?
}

interface MutableDoubleFactor : DailyAggregatedDoubleFactor {
    fun updateOnToday(key: String, value: Double)
    fun incrementOnToday(key: String)
    fun addObservation(key: String, value: Double)
}