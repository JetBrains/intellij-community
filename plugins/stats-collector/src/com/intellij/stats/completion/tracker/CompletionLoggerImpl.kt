// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.tracker

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.settings.CompletionMLRankingSettings
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.UISettings
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.stats.completion.events.*
import com.intellij.completion.ml.storage.LookupStorage
import com.intellij.completion.ml.util.CompletionUtil
import com.intellij.completion.ml.util.prefix
import com.intellij.stats.completion.LookupState

class CompletionFileLogger(private val installationUID: String,
                           private val completionUID: String,
                           private val bucket: String,
                           private val language: Language,
                           private val eventLogger: CompletionEventLogger) : CompletionLogger() {
  private val stateManager = LookupStateManager()

  private val ideVersion by lazy { ApplicationInfo.getInstance().build.asString() }

  override fun completionStarted(lookup: LookupImpl, isExperimentPerformed: Boolean, experimentVersion: Int, timestamp: Long) {
    val state = stateManager.update(lookup, false)

    val lookupStorage = LookupStorage.get(lookup)
    val pluginVersion = calcPluginVersion() ?: "pluginVersion"
    val mlRankingVersion = lookupStorage?.model?.version() ?: "NONE"

    val userFactors = lookupStorage?.userFactors ?: emptyMap()
    val contextFactors = lookupStorage?.contextFactors ?: emptyMap()

    val event = CompletionStartedEvent(
      ideVersion, pluginVersion, mlRankingVersion,
      installationUID, completionUID, language.displayName,
      isExperimentPerformed, experimentVersion,
      state, userFactors, contextFactors,
      prefixLength = lookup.prefix().length, bucket = bucket,
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
    val event = CustomMessageEvent(installationUID, completionUID, message, bucket, timestamp, language.displayName)
    eventLogger.log(event)
  }

  override fun afterCharTyped(c: Char, lookup: LookupImpl, timestamp: Long) {
    val state = stateManager.update(lookup, true)
    val event = TypeEvent(installationUID, completionUID, state, lookup.prefix().length, bucket, timestamp, language.displayName)
    event.fillCompletionParameters()

    eventLogger.log(event)
  }

  override fun downPressed(lookup: LookupImpl, timestamp: Long) {
    val state = stateManager.update(lookup, false)
    val event = DownPressedEvent(installationUID, completionUID, state, bucket, timestamp, language.displayName)
    event.fillCompletionParameters()

    eventLogger.log(event)
  }

  override fun upPressed(lookup: LookupImpl, timestamp: Long) {
    val state = stateManager.update(lookup, false)
    val event = UpPressedEvent(installationUID, completionUID, state, bucket, timestamp, language.displayName)
    event.fillCompletionParameters()

    eventLogger.log(event)
  }

  override fun completionCancelled(explicitly: Boolean, performance: Map<String, Long>, timestamp: Long) {
    val event = CompletionCancelledEvent(installationUID, completionUID, performance, explicitly, bucket, timestamp, language.displayName)
    eventLogger.log(event)
  }

  override fun itemSelectedByTyping(lookup: LookupImpl, performance: Map<String, Long>, timestamp: Long) {
    val state = stateManager.update(lookup, true)

    val event = TypedSelectEvent(installationUID, completionUID, state, state.selectedId, performance, bucket, timestamp, language.displayName)
    event.fillCompletionParameters()

    eventLogger.log(event)
  }

  override fun itemSelectedCompletionFinished(lookup: LookupImpl, completionChar: Char, performance: Map<String, Long>, timestamp: Long) {
    val state = stateManager.update(lookup, true)
    val event = ExplicitSelectEvent(installationUID, completionUID, state, state.selectedId, performance, completionChar,  bucket,
                                    timestamp, language.displayName)
    event.fillCompletionParameters()

    eventLogger.log(event)
  }

  override fun afterBackspacePressed(lookup: LookupImpl, timestamp: Long) {
    val state = stateManager.update(lookup, true)

    val event = BackspaceEvent(installationUID, completionUID, state, lookup.prefix().length, bucket, timestamp, language.displayName)
    event.fillCompletionParameters()

    eventLogger.log(event)
  }
}

private fun calcPluginVersion(): String? {
  val className = CompletionStartedEvent::class.java.name
  val id = PluginManagerCore.getPluginByClassName(className)
  val plugin = PluginManagerCore.getPlugin(id)
  return plugin?.version
}

private val LookupState.selectedId: Int
  get() = if (selectedPosition == -1) -1 else ids[selectedPosition]

private fun LookupStateLogData.fillCompletionParameters() {
  val params = CompletionUtil.getCurrentCompletionParameters()

  originalCompletionType = params?.completionType?.toString() ?: ""
  originalInvokationCount = params?.invocationCount ?: -1
}