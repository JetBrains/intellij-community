package com.intellij.completion

import com.intellij.openapi.application.ApplicationManager
import com.jetbrains.completion.ranker.features.BinaryFeatureInfo
import com.jetbrains.completion.ranker.features.CategoricalFeatureInfo
import com.jetbrains.completion.ranker.features.CompletionFactors
import com.jetbrains.completion.ranker.features.DoubleFeatureInfo

/**
 * @author Vitaliy.Bibaev
 */
interface FeatureManager {
    val featuresOrder: Map<String, Int>
    val binaryFactors: BinaryFeatureInfo
    val doubleFactors: DoubleFeatureInfo
    val categorialFactors: CategoricalFeatureInfo
    val ignoredFactors: Set<String>

    val allFeatures: CompletionFactors

    fun isUserFeature(name: String): Boolean

    companion object {
        fun getInstance(): FeatureManager = ApplicationManager.getApplication().getComponent(FeatureManager::class.java)
    }
}