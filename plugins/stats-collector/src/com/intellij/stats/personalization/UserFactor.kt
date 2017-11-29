package com.intellij.stats.personalization

import com.intellij.openapi.project.Project

/**
 * @author Vitaliy.Bibaev
 */
interface UserFactor {
    val id: String

    fun compute(project: Project): String?

    interface FeatureFactor : UserFactor {
        fun update(value: Any?)
    }
}
