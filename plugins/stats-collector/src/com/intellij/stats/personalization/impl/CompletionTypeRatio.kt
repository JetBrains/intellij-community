package com.intellij.stats.personalization.impl

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.stats.personalization.UserFactor
import com.intellij.stats.personalization.UserFactorDescriptions
import com.intellij.stats.personalization.UserFactorStorage

/**
 * @author Vitaliy.Bibaev
 */
class CompletionTypeRatio(private val type: CompletionType) : UserFactor {
    override val id: String = "CompletionTypeRatioOf$type"

    override fun compute(storage: UserFactorStorage): String? {
        val reader = storage.getFactorReader(UserFactorDescriptions.COMPLETION_TYPE)
        val total = reader.getTotalCompletionCount()
        return if (total == 0.0) "0.0" else (reader.getCompletionCountByType(type) / total).toString()
    }
}