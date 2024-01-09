package com.intellij.searchEverywhereMl.semantics.testCommands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.searchEverywhereMl.semantics.services.IndexingLifecycleTracker
import com.intellij.searchEverywhereMl.semantics.settings.SearchEverywhereSemanticSettings
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import org.jetbrains.annotations.NonNls

class WaitSemanticSearchIndexing(text: @NonNls String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "waitSemanticSearchIndexing"
    const val PREFIX = CMD_PREFIX + NAME
  }

  override suspend fun doExecute(context: PlaybackContext) {
    SearchEverywhereSemanticSettings.getInstance().enabledInActionsTab = true
    SearchEverywhereSemanticSettings.getInstance().enabledInFilesTab = true
    SearchEverywhereSemanticSettings.getInstance().enabledInSymbolsTab = true
    SearchEverywhereSemanticSettings.getInstance().enabledInClassesTab = true
    IndexingLifecycleTracker.getInstance(context.project).waitIndicesReady()
  }

  override fun getName() = NAME
}
