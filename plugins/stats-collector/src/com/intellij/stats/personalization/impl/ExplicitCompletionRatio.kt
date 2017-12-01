package com.intellij.stats.personalization.impl

import com.intellij.stats.personalization.UserFactor
import com.intellij.stats.personalization.UserFactorDescriptions
import com.intellij.stats.personalization.UserFactorStorage

/**
 * @author Vitaliy.Bibaev
 */
class ExplicitCompletionRatio : UserFactor {
    override val id: String = "explicitRatio"

    override fun compute(storage: UserFactorStorage): String? {
        val factorReader = storage.getFactorReader(UserFactorDescriptions.COMPLETION_FINISH_TYPE)
        val total = factorReader.getTotalExplicitSelectCount() + factorReader.getTotalTypedSelectCount()
        if (total == 0.0) return null
        return (factorReader.getTotalExplicitSelectCount() / total).toString()
    }
}