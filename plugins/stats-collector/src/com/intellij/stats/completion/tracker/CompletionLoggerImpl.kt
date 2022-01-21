// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.tracker

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.settings.CompletionMLRankingSettings
import com.intellij.completion.ml.storage.LookupStorage
import com.intellij.completion.ml.util.CompletionUtil
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.stats.completion.LookupState
import com.intellij.stats.completion.events.*

private val LOG = logger<CompletionFileLogger>()

class CompletionFileLogger(private val installationUID: String,
                           private val completionUID: String,
                           private val bucket: String,
                           private val languageName: String,
                           private val eventLogger: CompletionEventLogger) : CompletionLogger() {
  private val stateManager = LookupStateManager()

  private val ideVersion by lazy { ApplicationInfo.getInstance().build.asString() }

  override fun completionStarted(lookup: LookupImpl, prefixLength: Int, isExperimentPerformed: Boolean, experimentVersion: Int,
                                 timestamp: Long) {
    val state = stateManager.update(lookup, false)

    val lookupStorage = LookupStorage.get(lookup)
    val pluginVersion = calcPluginVersion() ?: "pluginVersion"
    val mlRankingVersion = lookupStorage?.model?.version() ?: "NONE"

    val userFactors = lookupStorage?.userFactors ?: emptyMap()
    val contextFactors = lookupStorage?.contextFactors ?: emptyMap()

    val event = CompletionStartedEvent(
      ideVersion, pluginVersion, mlRankingVersion,
      installationUID, completionUID, languageName,
      isExperimentPerformed, experimentVersion,
      state, userFactors, contextFactors,
      prefixLength = prefixLength, bucket = bucket,
      timestamp = lookupStorage?.startedTimestamp ?: timestamp)

    val shownTimestamp = CompletionUtil.getShownTimestamp(lookup)
    if (shownTimestamp != null) {
      event.lookupShownTime = shownTimestamp
    }

    event.isOneLineMode = lookup.editor.isOneLineMode
    event.isAutoPopup = CompletionUtil.getCurrentCompletionParameters()?.isAutoPopup
    event.fillCompletionParameters()
    event.additionalDetails["alphabetical"] = UISettings.instance.sortLookupElementsLexicographically.toString()
    if (lookupStorage != null) {
      if (lookupStorage.mlUsed() && CompletionMLRankingSettings.getInstance().isShowDiffEnabled) {
        event.additionalDetails["diff"] = "1"
      }
    }

    eventLogger.log(event)
  }

  override fun customMessage(message: String, timestamp: Long) {
    val event = CustomMessageEvent(installationUID, completionUID, message, bucket, timestamp, languageName)
    eventLogger.log(event)
  }

  override fun afterCharTyped(c: Char, lookup: LookupImpl, prefixLength: Int, timestamp: Long) {
    val state = stateManager.update(lookup, true)
    val event = TypeEvent(installationUID, completionUID, state, prefixLength, bucket, timestamp, languageName)
    event.fillCompletionParameters()

    eventLogger.log(event)
  }

  override fun downPressed(lookup: LookupImpl, timestamp: Long) {
    val state = stateManager.update(lookup, false)
    val event = DownPressedEvent(installationUID, completionUID, state, bucket, timestamp, languageName)
    event.fillCompletionParameters()

    eventLogger.log(event)
  }

  override fun upPressed(lookup: LookupImpl, timestamp: Long) {
    val state = stateManager.update(lookup, false)
    val event = UpPressedEvent(installationUID, completionUID, state, bucket, timestamp, languageName)
    event.fillCompletionParameters()

    eventLogger.log(event)
  }

  override fun completionCancelled(explicitly: Boolean, performance: Map<String, Long>, timestamp: Long) {
    val event = CompletionCancelledEvent(installationUID, completionUID, performance, explicitly, bucket, timestamp, languageName)
    eventLogger.log(event)
  }

  override fun itemSelectedByTyping(lookup: LookupImpl, performance: Map<String, Long>, timestamp: Long) {
    val state = stateManager.update(lookup, true)

    val event = TypedSelectEvent(installationUID, completionUID, state, state.selectedId, performance, bucket, timestamp, languageName)
    event.fillCompletionParameters()

    // remove after fixing EA-215359
    if (state.ids.isEmpty()) {
      LOG.error("Invalid state of lookup. Selected item [exists: ${lookup.currentItem != null}], but items list is empty.")
    }
    eventLogger.log(event)
  }

  override fun itemSelectedCompletionFinished(lookup: LookupImpl, completionChar: Char, performance: Map<String, Long>, timestamp: Long) {
    val state = stateManager.update(lookup, true)
    val event = ExplicitSelectEvent(installationUID, completionUID, state, state.selectedId, performance, completionChar,  bucket,
                                    timestamp, languageName)
    event.fillCompletionParameters()

    eventLogger.log(event)
  }

  override fun afterBackspacePressed(lookup: LookupImpl, prefixLength: Int, timestamp: Long) {
    val state = stateManager.update(lookup, true)

    val event = BackspaceEvent(installationUID, completionUID, state, prefixLength, bucket, timestamp, languageName)
    event.fillCompletionParameters()

    eventLogger.log(event)
  }
}

private fun calcPluginVersion(): String? {
  return PluginManager.getPluginByClass(CompletionStartedEvent::class.java)?.version
}

private val LookupState.selectedId: Int
  get() = if (selectedPosition == -1) -1 else ids[selectedPosition]

private fun LookupStateLogData.fillCompletionParameters() {
  val params = CompletionUtil.getCurrentCompletionParameters()

  originalCompletionType = params?.completionType?.toString() ?: ""
  originalInvokationCount = params?.invocationCount ?: -1
}