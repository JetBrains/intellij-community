package com.intellij.completion

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.jetbrains.completion.ranker.features.*

/**
 * @author Vitaliy.Bibaev
 */
class FeatureManagerImpl : FeatureManager, ApplicationComponent {
    companion object {
        fun getInstance(): FeatureManager = ApplicationManager.getApplication().getComponent(FeatureManager::class.java)
    }

    private lateinit var manager: FeatureManager

    override val binaryFactors: List<BinaryFeature> get() = manager.binaryFactors
    override val doubleFactors: List<DoubleFeature> get() = manager.doubleFactors
    override val categorialFactors: List<CatergorialFeature> get() = manager.categorialFactors
    override val ignoredFactors: Set<String> get() = manager.ignoredFactors
    override val completionFactors: CompletionFactors get() = manager.completionFactors
    override val featureOrder: Map<String, Int> get() = manager.featureOrder

    override fun createTransformer(): Transformer {
        return manager.createTransformer()
    }

    override fun isUserFeature(name: String): Boolean = false

    override fun initComponent() {
        manager = FeatureManagerFactory().createFeatureManager(FeatureReader, FeatureInterpreterImpl())
    }

    override fun allFeatures(): List<Feature> = manager.allFeatures()
}