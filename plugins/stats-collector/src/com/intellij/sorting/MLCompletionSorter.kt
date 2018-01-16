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
package com.intellij.sorting

import com.jetbrains.completion.ranker.features.FeatureManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.components.ServiceManager
import com.jetbrains.completion.ranker.CompletionRanker
import com.jetbrains.completion.ranker.features.Transformer


interface Ranker {

    /**
     * Items are sorted by descending order, so item with the highest rank will be on top
     * @param relevance map from LookupArranger.getRelevanceObjects
     */
    fun rank(relevance: Map<String, Any>, userFactors: Map<String, Any?>): Double

    companion object {
        fun getInstance(): Ranker = ServiceManager.getService(Ranker::class.java)
    }
}

class FeatureTransformerProvider(featureManager: FeatureManager) : ApplicationComponent {
    val featureTransformer: Transformer = featureManager.createTransformer()
}

class MLRanker(provider: FeatureTransformerProvider) : Ranker {

    private val featureTransformer = provider.featureTransformer
    private val ranker = CompletionRanker()

    override fun rank(relevance: Map<String, Any>, userFactors: Map<String, Any?>): Double {
        val featureArray = featureTransformer.featureArray(relevance, userFactors)
        return ranker.rank(featureArray)
    }

}