package com.intellij.stats.personalization

import com.intellij.stats.personalization.impl.DayImpl
import java.util.*

/**
 * @author Vitaliy.Bibaev
 */
object DateUtil {
    fun today(): Day = byDate(Date())

    fun byDate(date: Date): Day = DayImpl(date)
}