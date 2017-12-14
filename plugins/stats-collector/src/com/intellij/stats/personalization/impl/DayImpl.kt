package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.Day
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.*

/**
 * @author Vitaliy.Bibaev
 */
class DayImpl(date: Date) : Day {
    override val dayOfMonth: Int
    override val month: Int
    override val year: Int

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("dd-MM-yyyy")

        fun fromString(str: String): Day? {
            val position = ParsePosition(0)
            val date = DATE_FORMAT.parse(str, position)
            if (position.index == 0) return null
            return DayImpl(date)
        }
    }

    init {
        val calendar = Calendar.getInstance()
        calendar.time = date
        dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        month = calendar.get(Calendar.MONTH)
        year = calendar.get(Calendar.YEAR)
    }

    override fun compareTo(other: Day): Int {
        if (year == other.year) {
            if (month == other.month) {
                return dayOfMonth.compareTo(other.dayOfMonth)
            }
            return month.compareTo(other.month)
        }
        return year.compareTo(other.year)
    }

    override fun hashCode(): Int {
        return Objects.hash(year, month, dayOfMonth)
    }

    override fun equals(other: Any?): Boolean {
        if (other != null && other is Day) return compareTo(other) == 0
        return false
    }

    override fun toString(): String {
        return "$dayOfMonth-$month-$year"
    }
}
