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

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.impl.CompletionSorterImpl
import com.intellij.codeInsight.lookup.*
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.ui.OptionsTopHitProvider
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.util.ProcessingContext
import com.jetbrains.completion.ranker.features.CompletionState

typealias RelevanceObjects = MutableList<Pair<String, Any>>
typealias WeightedElement = Pair<LookupElement, Double>


private class WeightCache(val weight: Double, val prefixLength: Int)


class OrderingEmptyClassifer(next: Classifier<LookupElement>)
  : Classifier<LookupElement>(next, "before_rerank_order") {

  override fun getSortingWeights(source: MutableIterable<LookupElement>,
                                 ctx: ProcessingContext): MutableList<Pair<LookupElement, Any>> {
    return source
            .mapIndexed { index, lookupElement -> Pair(lookupElement, index as Any) }
            .toMutableList()
  }

  override fun classify(source: MutableIterable<LookupElement>, 
                        ctx: ProcessingContext): MutableIterable<LookupElement> {
    return source
  }
  
}


class MLClassifier(next: Classifier<LookupElement>,
                   private val lookup: LookupImpl,
                   private val ranker: Ranker) : Classifier<LookupElement>(next, "ml_rank") {

  private val cachedScore = mutableMapOf<LookupElement, WeightCache>()

  override fun classify(source: MutableIterable<LookupElement>, context: ProcessingContext): MutableIterable<LookupElement> {
    if (!isMlSortingEnabled()) return source
    
    val relevanceObjects = lookup.getRelevanceObjects(source, false)
    
    var position = 0 
    val elements = source.map {
      val weight = getWeight(it, position, relevanceObjects[it]!!)
      position++
      it to weight
    }
    
    val sorted = elements.sortedByDescending { it.second }
    return sorted.map { it.first }.toMutableList()
  }


  override fun getSortingWeights(items: MutableIterable<LookupElement>,
                                 context: ProcessingContext): MutableList<Pair<LookupElement, Any>> {
    return getWeightedElements(items) as MutableList<Pair<LookupElement, Any>>
  }

  private fun getWeightedElements(items: MutableIterable<LookupElement>): MutableList<WeightedElement> {
    var position = 0
    val pairs = mutableListOf<Pair<LookupElement, Double>>()
    items.forEach {
      val weight = getCachedWeight(it) ?: -777.666
      pairs.add(Pair.create(it, weight))
      position++
    }
    return pairs
  }

  private fun getCachedWeight(element: LookupElement): Double? {
    if (!isMlSortingEnabled()) return 777.7
    
    val currentPrefixLength = lookup.getPrefixLength(element)

    val cached = cachedScore[element]
    if (cached != null && currentPrefixLength == cached.prefixLength) {
      return cached.weight
    }

    return null
  }

  private fun getWeight(element: LookupElement, position: Int, relevance: RelevanceObjects): Double {
    val cachedWeight = getCachedWeight(element)
    if (cachedWeight != null) {
      return cachedWeight
    }
    
    
    val prefixLength = lookup.getPrefixLength(element)
    val elementLength = element.lookupString.length
    
    val state = CompletionState(position, query_length = prefixLength, cerp_length = 0, result_length = elementLength)
    val relevanceMap = relevance.groupBy { it.first }
    val calculatedWeight = ranker.rank(state, relevanceMap)
    
    cachedScore[element] = WeightCache(calculatedWeight, prefixLength)
    return calculatedWeight
  }

}





class PreservingOrderClassifierFactory: ClassifierFactory<LookupElement>("before_rerank_order") {
  override fun createClassifier(next: Classifier<LookupElement>): Classifier<LookupElement> {
    return OrderingEmptyClassifer(next)    
  }
}


class MLClassifierFactory(private val lookup: LookupImpl) : ClassifierFactory<LookupElement>("ml_rank") {
  
  override fun createClassifier(next: Classifier<LookupElement>): Classifier<LookupElement> {
    val ranker = Ranker.getInstance()
    return MLClassifier(next, lookup, ranker)
  }
  
}


open class MLCompletionContributor : CompletionContributor() {

  open fun newClassifierFactory(lookup: LookupImpl): ClassifierFactory<LookupElement> {
    return MLClassifierFactory(lookup)
  }
  
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (!isMlSortingEnabled()) return
    
    val oldSorter = getSorter(parameters, result)
    val lookup = LookupManager.getActiveLookup(parameters.editor) as LookupImpl

    val mlClassifierFactory = newClassifierFactory(lookup)
    val newSorter = oldSorter
            .withClassifier("templates", true, mlClassifierFactory)
            .withClassifier("ml_rank", false, PreservingOrderClassifierFactory())
    
    val newResult = result.withRelevanceSorter(newSorter)
    
    newResult.runRemainingContributors(parameters, {
      newResult.addElement(it.lookupElement)
    })
  }
  
}

fun isMlSortingEnabled(): Boolean = PropertiesComponent.getInstance().getBoolean("ml.sorting.enabled", true)
fun setMlSortingEnabled(value: Boolean) = PropertiesComponent.getInstance().setValue("ml.sorting.enabled", value, true)


fun getSorter(parameters: CompletionParameters, result: CompletionResultSet): CompletionSorterImpl {
  val field = result::class.java.getDeclaredField("mySorter")
  field.isAccessible = true
  return field.get(result) as CompletionSorterImpl
}