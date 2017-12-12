package com.intellij.completion

import com.intellij.openapi.application.ApplicationManager
import com.jetbrains.completion.ranker.features.*

/**
 * @author Vitaliy.Bibaev
 */
interface FeatureManager {
    val binaryFactors: List<BinaryFeature>
    val doubleFactors: List<DoubleFeature>
    val categorialFactors: List<CatergorialFeature>
    val ignoredFactors: Set<String>

    val completionFactors: CompletionFactors

    fun isUserFeature(name: String): Boolean
    fun allFeatures(): List<Feature>

    companion object {
        fun getInstance(): FeatureManager = ApplicationManager.getApplication().getComponent(FeatureManager::class.java)
    }
}