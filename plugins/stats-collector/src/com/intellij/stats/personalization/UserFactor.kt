package com.intellij.stats.personalization

import com.intellij.codeInsight.lookup.LookupElement

/**
 * @author Vitaliy.Bibaev
 */
interface UserFactor {
    val id: String

    fun store(element: LookupElement)
    fun extract(element: LookupElement): Comparable<Nothing>
}
