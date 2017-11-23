package com.intellij.stats.personalization

/**
 * @author Vitaliy.Bibaev
 */
interface UserFactor {
    val id: String

    fun compute(): String

    interface FeatureFactor : UserFactor {
        fun update(value: Any?)
    }
}
