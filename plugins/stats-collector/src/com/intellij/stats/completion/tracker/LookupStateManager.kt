// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.stats.completion.tracker

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.storage.LookupStorage
import com.intellij.completion.ml.util.RelevanceUtil
import com.intellij.completion.ml.util.idString
import com.intellij.stats.completion.LookupEntryInfo
import com.intellij.stats.completion.LookupState
import com.intellij.util.SlowOperations

class LookupStateManager {
    private val elementToId = mutableMapOf<String, Int>()
    private val idToEntryInfo = mutableMapOf<Int, LookupEntryInfo>()
    private val lookupStringToHash = mutableMapOf<String, Int>()
    private var currentSessionFactors: Map<String, String> = emptyMap()

  fun update(lookup: LookupImpl, factorsUpdated: Boolean): LookupState {
    return SlowOperations.allowSlowOperations<LookupState, Throwable> {
      doUpdate(lookup, factorsUpdated)
    }
  }

  private fun doUpdate(lookup: LookupImpl, factorsUpdated: Boolean): LookupState {
        val ids = mutableListOf<Int>()
        val newIds = mutableSetOf<Int>()

        val items = lookup.items

        val currentPosition = items.indexOf(lookup.currentItem)

        val elementToId = mutableMapOf<LookupElement, Int>()
        for (item in items) {
            var id = getElementId(item)
            if (id == null) {
                id = registerElement(item)
                newIds.add(id)
            }
            elementToId[item] = id
            ids.add(id)
        }

        val storage = LookupStorage.get(lookup)
        val commonSessionFactors = storage?.sessionFactors?.getLastUsedCommonFactors() ?: emptyMap()
        val sessionFactorsToLog = computeSessionFactorsToLog(commonSessionFactors)

        if (factorsUpdated) {
            val infos = items.toLookupInfos(lookup, elementToId)
            val newInfos = infos.filter { it.id in newIds }

            val itemsDiff = infos.mapNotNull { idToEntryInfo[it.id]?.calculateDiff(it) }
            infos.forEach { idToEntryInfo[it.id] = it }
            return LookupState(ids, newInfos, itemsDiff, currentPosition, sessionFactorsToLog)
        }
        else {
            val newItems = items.filter { getElementId(it) in newIds }.toLookupInfos(lookup, elementToId)
            newItems.forEach { idToEntryInfo[it.id] = it }
            return LookupState(ids, newItems, emptyList(), currentPosition, sessionFactorsToLog)
        }
    }

    fun getElementId(item: LookupElement): Int? {
        val itemString = item.idString()
        return elementToId[itemString]
    }

    private fun computeSessionFactorsToLog(factors: Map<String, String>): Map<String, String> {
        if (factors == currentSessionFactors) return emptyMap()
        currentSessionFactors = factors
        return factors
    }

    private fun registerElement(item: LookupElement): Int {
        val itemString = item.idString()
        val newId = elementToId.size
        elementToId[itemString] = newId
        return newId
    }

    private fun List<LookupElement>.toLookupInfos(lookup: LookupImpl, elementToId: Map<LookupElement, Int>): List<LookupEntryInfo> {
        val item2relevance = calculateRelevance(lookup, this)
        return this.map { lookupElement ->
            val lookupString = lookupElement.lookupString
            val itemHash = getLookupStringHash(lookupString)
            LookupEntryInfo(elementToId.getValue(lookupElement), lookupString.length, itemHash, item2relevance.getValue(lookupElement))
        }
    }

    private fun calculateRelevance(lookup: LookupImpl, items: List<LookupElement>): Map<LookupElement, Map<String, String>> {
        val lookupStorage = LookupStorage.get(lookup)
        if (lookupStorage?.shouldComputeFeatures() == false) {
            return items.associateBy({ it }, { emptyMap() })
        }

        val result = mutableMapOf<LookupElement, Map<String, String>>()
        if (lookupStorage != null) {
            for (item in items) {
                val factors = lookupStorage.getItemStorage(item.idString()).getLastUsedFactors()?.mapValues { it.value.toString() }
                if (factors != null) {
                    result[item] = factors
                }
            }
        }

        // fallback (get factors from the relevance objects)
        val rest = items.filter { it !in result }
        if (rest.isNotEmpty()) {
            val relevanceObjects = lookup.getRelevanceObjects(rest, false)
            for (item in rest) {
                val relevanceMap: Map<String, String> = relevanceObjects[item]?.let { objects ->
                    val (relevanceMap, additionalMap) = RelevanceUtil.asRelevanceMaps(objects)
                    val features = mutableMapOf<String, String>()
                    relevanceMap.forEach { features[it.key] = it.value.toString() }
                    additionalMap.forEach { features[it.key] = it.value.toString() }
                    return@let features
                } ?: emptyMap()
                result[item] = relevanceMap
            }
        }

        return result
    }

    private fun getLookupStringHash(lookupString: String): Int {
        return lookupStringToHash.computeIfAbsent(lookupString) { lookupStringToHash.size }
    }
}