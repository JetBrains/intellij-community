package com.intellij.stats.personalization.impl

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.project.Project
import com.intellij.stats.personalization.UserFactor
import com.intellij.stats.personalization.UserFactorDescriptions
import com.intellij.stats.personalization.UserFactorStorage

/**
 * @author Vitaliy.Bibaev
 */
class CompletionTypeRatio(private val type: CompletionType) : UserFactor {
    override val id: String = "${type}_ratio"

    override fun compute(project: Project): String? {
        val reader = UserFactorStorage.getInstance().getFactorReader(UserFactorDescriptions.COMPLETION_TYPE)
        val total = reader.getTotalCompletionCount()
        return if (total == 0.0) "0.0" else (reader.getCompletionCountByType(type) / total).toString()
    }
}