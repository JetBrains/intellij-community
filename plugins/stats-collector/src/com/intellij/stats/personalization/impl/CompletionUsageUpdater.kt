package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.UserFactorUpdaterBase

/**
 * @author Vitaliy.Bibaev
 */
class CompletionUsageUpdater(factor: MutableDoubleFactor) : UserFactorUpdaterBase(factor) {
    fun fireCompletionUsed() {
        factor.incrementOnToday("count")
    }
}