package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.FactorUpdater

/**
 * @author Vitaliy.Bibaev
 */
class CompletionFinishTypeUpdater(private val factor: MutableDoubleFactor) : FactorUpdater {
    companion object {
        val EXPLICIT_SELECT_KEY = "explicitSelect"
        val TYPED_SELECT_KEY = "typedSelect"
    }

    fun fireExplicitCompletionPerformed() {
        factor.incrementOnToday(EXPLICIT_SELECT_KEY)
    }

    fun fireTypedSelectPerformed() {
        factor.incrementOnToday(TYPED_SELECT_KEY)
    }
}