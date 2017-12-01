package com.intellij.stats.personalization.impl

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.stats.personalization.FactorUpdater

/**
 * @author Vitaliy.Bibaev
 */
class CompletionTypeUpdater(private val factor: MutableDoubleFactor) : FactorUpdater {
    fun fireCompletionPerformed(type: CompletionType) {
        factor.incrementOnToday(type.toString())
    }
}