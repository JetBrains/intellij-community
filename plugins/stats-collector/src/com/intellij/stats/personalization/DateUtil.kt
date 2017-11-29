package com.intellij.stats.personalization

import java.text.SimpleDateFormat
import java.util.*

/**
 * @author Vitaliy.Bibaev
 */
object DateUtil {
    private val DATE_FORMAT = SimpleDateFormat("dd-MM-yyyy")

    fun today(): String = byDate(Date())

    fun parse(date: String): Date = DATE_FORMAT.parse(date)

    fun byDate(date: Date): String = DATE_FORMAT.format(date)

    fun normalize(date: Date): Date = parse(byDate(date))
}