// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.stats.completion.tracker

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.completion.ml.experiment.ExperimentInfo
import com.intellij.completion.ml.storage.LookupStorage
import com.intellij.completion.ml.util.prefix

class CompletionActionsTracker(private val lookup: LookupImpl,
                               private val lookupStorage: LookupStorage,
                               private val logger: CompletionLogger,
                               private val experimentInfo: ExperimentInfo)
    : CompletionActionsListener {

    private var completionStarted = false
    private var selectedByDotTyping = false
    private var prefixLength = 0

    private val deferredLog = DeferredLog()

    private fun isCompletionActive(): Boolean {
        return completionStarted && !lookup.isLookupDisposed
                || ApplicationManager.getApplication().isUnitTestMode
    }

    override fun lookupCanceled(event: LookupEvent) {
        if (!completionStarted) return

        val timestamp = System.currentTimeMillis()
        deferredLog.log()
        val currentItem = lookup.currentItem
        val performance = lookupStorage.performanceTracker.measurements()

        if (isSelectedByTyping(currentItem) || selectedByDotTyping) {
            logger.itemSelectedByTyping(lookup, performance, timestamp)
        }
        else {
            logger.completionCancelled(event.isCanceledExplicitly, performance, timestamp)
        }
    }

    override fun currentItemChanged(event: LookupEvent) {
        if (completionStarted) {
            return
        }

        val timestamp = System.currentTimeMillis()
        completionStarted = true
        prefixLength = lookup.prefix().length
        deferredLog.defer {
            logger.completionStarted(lookup, prefixLength, experimentInfo.inExperiment, experimentInfo.version, timestamp)
        }
    }

    override fun itemSelected(event: LookupEvent) {
        if (!completionStarted) return

        val timestamp = System.currentTimeMillis()
        deferredLog.log()
        val performance = lookupStorage.performanceTracker.measurements()
        if (isSelectedByTyping(lookup.currentItem)) {
            logger.itemSelectedByTyping(lookup, performance, timestamp)
        }
        else {
            logger.itemSelectedCompletionFinished(lookup, event.completionChar, performance, timestamp)
        }
    }

    override fun beforeDownPressed() {
        deferredLog.log()
    }

    override fun downPressed() {
        if (!isCompletionActive()) return

        val timestamp = System.currentTimeMillis()
        deferredLog.log()
        deferredLog.defer {
            logger.downPressed(lookup, timestamp)
        }
    }

    override fun beforeUpPressed() {
        deferredLog.log()
    }

    override fun upPressed() {
        if (!isCompletionActive()) return

        val timestamp = System.currentTimeMillis()
        deferredLog.log()
        deferredLog.defer {
            logger.upPressed(lookup, timestamp)
        }
    }

    override fun beforeBackspacePressed() {
        if (!isCompletionActive()) return
        deferredLog.log()
    }

    override fun afterBackspacePressed() {
        if (!isCompletionActive()) return

        val timestamp = System.currentTimeMillis()
        prefixLength--
        deferredLog.log()
        deferredLog.defer {
            logger.afterBackspacePressed(lookup, prefixLength, timestamp)
        }
    }

    override fun beforeCharTyped(c: Char) {
        if (!isCompletionActive()) return

        val timestamp = System.currentTimeMillis()
        deferredLog.log()

        if (c == '.') {
            val item = lookup.currentItem
            if (item == null) {
                logger.customMessage("Before typed $c lookup.currentItem is null; lookup size: ${lookup.items.size}", timestamp)
                return
            }
            val text = lookup.itemPattern(item)
            if (item.lookupString == text) {
                selectedByDotTyping = true
            }
        }
    }


    override fun afterAppend(c: Char) {
        if (!isCompletionActive() || !c.isJavaIdentifierPart()) return

        val timestamp = System.currentTimeMillis()
        prefixLength++
        deferredLog.log()
        deferredLog.defer {
            logger.afterCharTyped(c, lookup, prefixLength, timestamp)
        }
    }

    private fun isSelectedByTyping(item: LookupElement?): Boolean {
        if (item != null) {
            val pattern = lookup.itemPattern(item)
            return item.lookupString == pattern
        }
        return false
    }
}
