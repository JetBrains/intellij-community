package com.intellij.sorting

import com.intellij.codeInsight.completion.CompletionFinalSorter
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.util.Pair
import com.jetbrains.completion.ranker.features.CompletionState
import com.jetbrains.completion.ranker.features.FeatureUtils

class MLSorterFactory : CompletionFinalSorter.Factory {
    override fun newSorter() = MLSorter()
}

class MLSorter : CompletionFinalSorter() {

    private val ranker = Ranker.getInstance()
    private val cachedScore = mutableMapOf<LookupElement, ItemRankInfo>()

    override fun getRelevanceObjects(items: MutableIterable<LookupElement>): Map<LookupElement, List<Pair<String, Any>>> {
        if (!isMlSortingEnabled()) {
            return items.associate { it to listOf(Pair.create(FeatureUtils.ML_RANK, FeatureUtils.NONE as Any)) }
        }
        
        val isUnknownFeaturesPresent = items.find { cachedScore[it] == null } != null
        if (isUnknownFeaturesPresent) {
            return items.associate { it to listOf(Pair.create(FeatureUtils.ML_RANK, FeatureUtils.UNDEFINED as Any)) }
        }
        
        return items.associate {
            val result = mutableListOf<Pair<String, Any>>()
            val cached = cachedScore[it]
            if (cached != null) {
                result.add(Pair.create(FeatureUtils.ML_RANK, cached.mlRank))
                result.add(Pair.create(FeatureUtils.BEFORE_ORDER, cached.positionBefore))
            }
            it to result
        }
    }

    override fun sort(items: MutableIterable<LookupElement>, parameters: CompletionParameters): Iterable<LookupElement> {
        if (!isMlSortingEnabled()) return items

        val lookup = LookupManager.getActiveLookup(parameters.editor) as? LookupImpl ?: return items
        val relevanceObjects = lookup.getRelevanceObjects(items, false)

        val startTime = System.currentTimeMillis()
        val sorted = sortInner(items, lookup, relevanceObjects)
        val timeSpent = System.currentTimeMillis() - startTime
        
        val elementsSorted = items.count()
        SortingTimeStatistics.registerSortTiming(elementsSorted, timeSpent)
        
        return sorted
    }

    private fun sortInner(items: MutableIterable<LookupElement>, 
                          lookup: LookupImpl, 
                          relevanceObjects: Map<LookupElement, List<Pair<String, Any>>>): Iterable<LookupElement> 
    {
        return items
                .mapIndexed { index, lookupElement ->
                    val relevance = relevanceObjects[lookupElement] ?: emptyList()
                    val rank = calculateElementRank(lookup, lookupElement, index, relevance) ?: return items
                    lookupElement to rank
                }
                .sortedByDescending { it.second }
                .map { it.first }
    }


    private fun getCachedRankInfo(lookup: LookupImpl, element: LookupElement): ItemRankInfo? {
        val currentPrefixLength = lookup.getPrefixLength(element)

        val cached = cachedScore[element]
        if (cached != null && currentPrefixLength == cached.prefixLength) {
            return cached
        }

        return null
    }


    private fun calculateElementRank(lookup: LookupImpl, 
                                     element: LookupElement, 
                                     position: Int, 
                                     relevance: LookupElementRelevance): Double? 
    {
        val cachedWeight = getCachedRankInfo(lookup, element)
        if (cachedWeight != null) {
            return cachedWeight.mlRank
        }

        val prefixLength = lookup.getPrefixLength(element)
        val elementLength = element.lookupString.length

        val state = CompletionState(position, query_length = prefixLength, cerp_length = 0, result_length = elementLength)
        val relevanceMap = relevance.groupBy { it.first }
        val mlRank = ranker.rank(state, relevanceMap) ?: return null

        val info = ItemRankInfo(position, mlRank, prefixLength)
        cachedScore[element] = info
        return info.mlRank
    }


}


private class ItemRankInfo(val positionBefore: Int, val mlRank: Double, val prefixLength: Int)

typealias LookupElementRelevance = List<Pair<String, Any>>
typealias WeightedElement = Pair<LookupElement, Double>