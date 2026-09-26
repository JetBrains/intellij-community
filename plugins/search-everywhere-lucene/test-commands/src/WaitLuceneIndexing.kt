package com.intellij.searchEverywhereLucene.performanceTesting

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.searchEverywhereLucene.backend.providers.files.FileIndex
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import org.jetbrains.annotations.NonNls

class WaitLuceneIndexing(text: @NonNls String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME: String = "waitLuceneIndexing"
    const val PREFIX: String = CMD_PREFIX + NAME
  }

  override suspend fun doExecute(context: PlaybackContext) {
    FileIndex.getInstanceIfEnabled(context.project)?.awaitInitialIndexing()
  }

  override fun getName(): String = NAME
}
