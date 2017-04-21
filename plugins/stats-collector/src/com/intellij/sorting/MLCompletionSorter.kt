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

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.Pair
import com.jetbrains.completion.ranker.CompletionRanker
import com.jetbrains.completion.ranker.features.*


interface Ranker {

    /**
     * Items are sorted by descending order, so item with the highest rank will be on top
     */
    fun rank(state: CompletionState, lookupRelevance: List<Pair<String, Any>>): Double?

    companion object {
        fun getInstance(): Ranker = ServiceManager.getService(Ranker::class.java)
    }
}


class FeatureTransformerProvider: ApplicationComponent.Adapter() {

    lateinit var featureTransformer: FeatureTransformer
        private set
    
    override fun initComponent() {
        val binary = binaryFactors()
        val double = doubleFactors()
        val categorical = categoricalFactors()
        val factors = completionFactors()
        val order = featuresOrder()
        val ignored = ignoredFactors()
        
        featureTransformer = FeatureTransformer(
                binary, 
                double, 
                categorical, 
                order, 
                factors,
                IgnoredFactorsMatcher(ignored)
        )
    }
    
}



class MLRanker(val provider: FeatureTransformerProvider): Ranker {

    private val featureTransformer = provider.featureTransformer
    private val ranker = CompletionRanker()
    
    override fun rank(state: CompletionState, lookupRelevance: List<Pair<String, Any>>): Double? {
        val map = featuresMap(lookupRelevance)
        val featureArray = featureTransformer.featureArray(state, map)
        if (featureArray != null) {
            return ranker.rank(featureArray)
        }
        return null
    }
    
}

fun featuresMap(lookupRelevance: List<Pair<String, Any>>): Map<String, Any> {
    val map = HashMap<String, Any>()
    lookupRelevance.forEach {
        if (it.first == "proximity") {
            val proximityValue = it.second?.toString() ?: return@forEach
            map.putAll(proximityValue.toProximityMap())
        } else {
            map.put(it.first, it.second)
        }
    }
    return map
}

/**
 * Proximity features now came like [samePsiFile=true, openedInEditor=false], need to convert to proper map
 */
fun String.toProximityMap(): Map<String, Any> {
    val items = replace("[", "").replace("]", "").split(",")

    return items.map { 
        val (key, value) = it.trim().split("=")
        "prox_$key" to value
    }.toMap()
}

fun isMlSortingEnabled(): Boolean = PropertiesComponent.getInstance().getBoolean("ml.sorting.enabled", true)

fun setMlSortingEnabled(value: Boolean) = PropertiesComponent.getInstance().setValue("ml.sorting.enabled", value, true)