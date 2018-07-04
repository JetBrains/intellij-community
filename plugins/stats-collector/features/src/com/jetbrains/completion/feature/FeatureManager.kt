/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package com.jetbrains.completion.feature

import com.jetbrains.completion.feature.impl.CompletionFactors
import com.jetbrains.completion.feature.impl.FeatureReader

/**
 * @author Vitaliy.Bibaev
 */
interface FeatureManager {
    val binaryFactors: List<BinaryFeature>
    val doubleFactors: List<DoubleFeature>
    val categoricalFactors: List<CategoricalFeature>
    val ignoredFactors: Set<String>
    val featureOrder: Map<String, Int>

    val completionFactors: CompletionFactors

    fun isUserFeature(name: String): Boolean
    fun allFeatures(): List<Feature>

    fun createTransformer(): Transformer

    interface Factory {
        fun createFeatureManager(reader: FeatureReader, interpreter: FeatureInterpreter): FeatureManager
    }
}