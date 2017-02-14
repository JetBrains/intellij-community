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
package com.intellij.stats.completion.sorting

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.impl.CompletionSorterImpl
import com.intellij.codeInsight.lookup.*
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.util.Pair
import com.intellij.util.ProcessingContext
import com.jetbrains.completion.ranker.features.CompletionState

typealias RelevanceObjects = MutableList<Pair<String, Any>>
typealias WeightedElement = Pair<LookupElement, Double>

class MLClassifier(next: Classifier<LookupElement>,
                   private val lookupArranger: LookupArranger,
                   private val lookup: LookupImpl,
                   private val ranker: MLRanker) : Classifier<LookupElement>(next, "MLClassifier") {

  private val cachedScore = mutableMapOf<LookupElement, Double>()

  override fun classify(source: MutableIterable<LookupElement>, context: ProcessingContext): MutableIterable<LookupElement> {
    val relevanceObjects: Map<LookupElement, MutableList<Pair<String, Any>>> = lookupArranger.getRelevanceObjects(source, false)
    
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
      val weight = -777.666
      pairs.add(Pair.create(it, weight))
      position++
    }
    return pairs
  }

  fun getWeight(element: LookupElement, position: Int, relevance: RelevanceObjects): Double {
    val cached = cachedScore[element]
    if (cached != null) {
      return cached
    }

    val elementLength = element.lookupString.length
    //todo get it from anywhere
    val prefixLength = 0
    val state = CompletionState(position, query_length = prefixLength, cerp_length = 0, result_length = elementLength)
    val relevanceMap = relevance.groupBy { it.first }
    
    val calculatedWeight = ranker.rank(state, relevanceMap)
    cachedScore[element] = calculatedWeight

    return calculatedWeight
  }

}

class MLClassifierFactory(
        private val lookupArranger: LookupArranger,
        private val lookup: LookupImpl) : ClassifierFactory<LookupElement>("MLClassifierFactory") {
  
  private val ranker = MLRanker.getInstance()
  
  override fun createClassifier(next: Classifier<LookupElement>): Classifier<LookupElement> {
    return MLClassifier(next, lookupArranger, lookup, ranker)
  }
}


class MLCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val oldSorter = getSorter(result)
    val lookupArranger = getLookupArranger(parameters)

    val lookup = LookupManager.getActiveLookup(parameters.editor) as LookupImpl
    val newSorter = oldSorter.withClassifier("templates", true, MLClassifierFactory(lookupArranger, lookup))
    val newResult = result.withRelevanceSorter(newSorter)
    
    newResult.runRemainingContributors(parameters, {
      newResult.addElement(it.lookupElement)
    })
  }
  
}


fun getSorter(result: CompletionResultSet): CompletionSorterImpl {
  val field = result.javaClass.getDeclaredField("mySorter")
  field.isAccessible = true
  return field.get(result) as CompletionSorterImpl
}

fun getLookupArranger(parameters: CompletionParameters): LookupArranger {
  val lookup = LookupManager.getActiveLookup(parameters.editor) as LookupImpl
  val arrangerField = lookup.javaClass.getDeclaredField("myArranger")
  arrangerField.isAccessible = true
  val arranger = arrangerField.get(lookup) as LookupArranger
  return arranger
}
