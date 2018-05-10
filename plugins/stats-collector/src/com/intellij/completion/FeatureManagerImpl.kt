/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.completion

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.jetbrains.completion.feature.*
import com.jetbrains.completion.feature.impl.CompletionFactors
import com.jetbrains.completion.feature.impl.FeatureInterpreterImpl
import com.jetbrains.completion.feature.impl.FeatureManagerFactory
import com.jetbrains.completion.feature.impl.FeatureReader

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
    override val categoricalFactors: List<CategoricalFeature> get() = manager.categoricalFactors
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