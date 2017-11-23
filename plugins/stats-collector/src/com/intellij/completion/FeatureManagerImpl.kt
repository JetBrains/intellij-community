package com.intellij.completion

import com.intellij.openapi.components.ApplicationComponent
import com.jetbrains.completion.ranker.features.*

/**
 * @author Vitaliy.Bibaev
 */
class FeatureManagerImpl : FeatureManager, ApplicationComponent {
    override lateinit var binaryFactors: BinaryFeatureInfo private set
    override lateinit var doubleFactors: DoubleFeatureInfo private set
    override lateinit var categorialFactors: CategoricalFeatureInfo private set
    override lateinit var allFeatures: CompletionFactors private set
    override lateinit var featuresOrder: Map<String, Int> private set
    override lateinit var ignoredFactors: Set<String> private set

    override fun isUserFeature(name: String): Boolean = false

    override fun initComponent() {
        binaryFactors = FeatureReader.binaryFactors()
        doubleFactors = FeatureReader.doubleFactors()
        allFeatures = FeatureReader.completionFactors()
        categorialFactors = FeatureReader.categoricalFactors()
        featuresOrder = FeatureReader.featuresOrder()
        ignoredFactors = FeatureReader.ignoredFactors()
    }
}