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
package com.intellij.stats.completion


import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.tracker.LookupElementPositionTracker
import com.intellij.ide.plugins.PluginManager
import com.intellij.stats.completion.events.*
import com.intellij.stats.personalization.UserFactorsManager


class CompletionFileLogger(private val installationUID: String,
                           private val completionUID: String,
                           private val eventLogger: CompletionEventLogger) : CompletionLogger() {

    private val elementToId = mutableMapOf<String, Int>()

    private fun registerElement(item: LookupElement): Int {
        val itemString = item.idString()
        val newId = elementToId.size
        elementToId[itemString] = newId
        return newId
    }

    private fun getElementId(item: LookupElement): Int? {
        val itemString = item.idString()
        return elementToId[itemString]
    }

    private fun getRecentlyAddedLookupItems(items: List<LookupElement>): List<LookupElement> {
        val newElements = items.filter { getElementId(it) == null }
        newElements.forEach {
            registerElement(it)
        }
        return newElements
    }

    private fun List<LookupElement>.toLookupInfos(lookup: LookupImpl): List<LookupEntryInfo> {
        val relevanceObjects = lookup.getRelevanceObjects(this, false)
        return this.map {
            val id = getElementId(it)!!
            val relevanceMap = relevanceObjects[it]?.map { Pair(it.first, it.second?.toString()) }?.toMap()
            LookupEntryInfo(id, it.lookupString.length, relevanceMap)
        }
    }

    override fun completionStarted(lookup: LookupImpl, isExperimentPerformed: Boolean, experimentVersion: Int) {
        val lookupItems = lookup.items

        lookupItems.forEach { registerElement(it) }
        val relevanceObjects = lookup.getRelevanceObjects(lookupItems, false)

        val lookupEntryInfos = lookupItems.map {
            val id = getElementId(it)!!
            val relevanceMap = relevanceObjects[it]?.map { Pair(it.first, it.second?.toString()) }?.toMap()
            LookupEntryInfo(id, it.lookupString.length, relevanceMap)
        }

        val language = lookup.language()

        val ideVersion = PluginManager.BUILD_NUMBER ?: "ideVersion"
        val pluginVersion = calcPluginVersion() ?: "pluginVersion"
        val mlRankingVersion = "NONE"

        val userFactors = lookup.getUserData(UserFactorsManager.USER_FACTORS_KEY) ?: emptyMap()

        val event = CompletionStartedEvent(
                ideVersion, pluginVersion, mlRankingVersion,
                installationUID, completionUID,
                language?.displayName,
                isExperimentPerformed, experimentVersion,
                lookupEntryInfos, userFactors, selectedPosition = 0)

        event.isOneLineMode = lookup.editor.isOneLineMode
        event.fillCompletionParameters()

        eventLogger.log(event)
    }

    override fun customMessage(message: String) {
        val event = CustomMessageEvent(installationUID, completionUID, message)
        eventLogger.log(event)
    }

    override fun afterCharTyped(c: Char, lookup: LookupImpl) {
        val lookupItems = lookup.items
        val newItems = getRecentlyAddedLookupItems(lookupItems).toLookupInfos(lookup)
        
        val ids = lookupItems.map { getElementId(it)!! }
        val currentPosition = lookupItems.indexOf(lookup.currentItem)

        val event = TypeEvent(installationUID, completionUID, ids, newItems, currentPosition)
        event.fillCompletionParameters()

        eventLogger.log(event)
    }

    override fun downPressed(lookup: LookupImpl) {
        val lookupItems = lookup.items
        
        val newInfos = getRecentlyAddedLookupItems(lookupItems).toLookupInfos(lookup)
        val ids = if (newInfos.isNotEmpty()) lookupItems.map { getElementId(it)!! } else emptyList()
        val currentPosition = lookupItems.indexOf(lookup.currentItem)

        val event = DownPressedEvent(installationUID, completionUID, ids, newInfos, currentPosition)
        event.fillCompletionParameters()

        eventLogger.log(event)
    }

    override fun upPressed(lookup: LookupImpl) {
        val lookupItems = lookup.items
        
        val newInfos = getRecentlyAddedLookupItems(lookupItems).toLookupInfos(lookup)
        val ids = if (newInfos.isNotEmpty()) lookupItems.map { getElementId(it)!! } else emptyList()
        val currentPosition = lookupItems.indexOf(lookup.currentItem)

        val event = UpPressedEvent(installationUID, completionUID, ids, newInfos, currentPosition)
        event.fillCompletionParameters()

        eventLogger.log(event)
    }

    override fun completionCancelled() {
        val event = CompletionCancelledEvent(installationUID, completionUID)
        eventLogger.log(event)
    }

    override fun itemSelectedByTyping(lookup: LookupImpl) {
        val newCompletionElements = getRecentlyAddedLookupItems(lookup.items).toLookupInfos(lookup)
        val id = currentItemInfo(lookup).id

        val history = lookup.itemsHistory()
        val completionList = lookup.items.toLookupInfos(lookup)

        val event = TypedSelectEvent(installationUID, completionUID, newCompletionElements, id, completionList, history)
        event.fillCompletionParameters()

        eventLogger.log(event)
    }

    override fun itemSelectedCompletionFinished(lookup: LookupImpl) {
        val newCompletionItems = getRecentlyAddedLookupItems(lookup.items).toLookupInfos(lookup)
        val (index, id) = currentItemInfo(lookup)

        val history = lookup.itemsHistory()
        val completionList = lookup.items.toLookupInfos(lookup)

        val event = ExplicitSelectEvent(installationUID, completionUID, newCompletionItems, index, id, completionList, history)
        event.fillCompletionParameters()

        eventLogger.log(event)
    }

    private fun currentItemInfo(lookup: LookupImpl): CurrentElementInfo {
        val current = lookup.currentItem
        return if (current != null) {
            val index = lookup.items.indexOf(current)
            val id = getElementId(current)!!
            CurrentElementInfo(index, id)
        } else {
            CurrentElementInfo(-1, -1)
        }
    }

    private fun LookupImpl.itemsHistory(): Map<Int, ElementPositionHistory> {
        val positionTracker = LookupElementPositionTracker.getInstance()
        return items.map { getElementId(it)!! to positionTracker.positionsHistory(this, it).let { ElementPositionHistory(it) } }.toMap()
    }
    
    override fun afterBackspacePressed(lookup: LookupImpl) {
        val lookupItems = lookup.items
        
        val newInfos = getRecentlyAddedLookupItems(lookupItems).toLookupInfos(lookup)
        val ids = lookupItems.map { getElementId(it)!! }
        val currentPosition = lookupItems.indexOf(lookup.currentItem)

        val event = BackspaceEvent(installationUID, completionUID, ids, newInfos, currentPosition)
        event.fillCompletionParameters()

        eventLogger.log(event)
    }

}


private class CurrentElementInfo(val index: Int, val id: Int) {
    operator fun component1() = index
    operator fun component2() = id
}


private fun calcPluginVersion(): String? {
    val className = CompletionStartedEvent::class.java.name
    val id = PluginManager.getPluginByClassName(className)
    val plugin = PluginManager.getPlugin(id)
    return plugin?.version
}


private fun LookupStateLogData.fillCompletionParameters() {
    val params = CompletionUtil.getCurrentCompletionParameters()

    originalCompletionType = params?.completionType?.toString() ?: ""
    originalInvokationCount = params?.invocationCount ?: -1
}