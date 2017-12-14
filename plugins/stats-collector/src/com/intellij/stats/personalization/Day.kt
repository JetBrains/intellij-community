package com.intellij.stats.personalization

/**
 * @author Vitaliy.Bibaev
 */
interface Day : Comparable<Day> {
    val dayOfMonth: Int
    val month: Int
    val year: Int
}