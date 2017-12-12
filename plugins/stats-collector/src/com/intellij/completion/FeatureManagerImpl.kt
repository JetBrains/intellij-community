package com.intellij.completion

import com.intellij.openapi.components.ApplicationComponent
import com.jetbrains.completion.ranker.features.*

/**
 * @author Vitaliy.Bibaev
 */
class FeatureManagerImpl : FeatureManager, ApplicationComponent {
    private var arrayLength = 0

    override lateinit var binaryFactors: List<BinaryFeature> private set
    override lateinit var doubleFactors: List<DoubleFeature> private set
    override lateinit var categorialFactors: List<CatergorialFeature> private set
    override lateinit var completionFactors: CompletionFactors private set
    override lateinit var ignoredFactors: Set<String> private set
    private lateinit var allFeatures: List<Feature>

    override val featureArrayLength: Int
        get() = arrayLength

    override fun isUserFeature(name: String): Boolean = false

    override fun initComponent() {
        val order = FeatureReader.featuresOrder()
        val interpreter = FeatureInterpreterImpl()

        binaryFactors = FeatureReader.binaryFactors()
                .map { (name, description) -> interpreter.binary(name, description, order) }
        doubleFactors = FeatureReader.doubleFactors()
                .map { (name, defaultValue) -> interpreter.double(name, defaultValue, order) }
        categorialFactors = FeatureReader.categoricalFactors()
                .map { (name, categories) -> interpreter.categorial(name, categories, order) }

        completionFactors = FeatureReader.completionFactors()

        ignoredFactors = FeatureReader.ignoredFactors()

        arrayLength = order.size
        val features: ArrayList<Feature> = ArrayList(binaryFactors)
        features.addAll(doubleFactors)
        features.addAll(categorialFactors)
        allFeatures = features
    }

    override fun allFeatures(): List<Feature> = allFeatures
}