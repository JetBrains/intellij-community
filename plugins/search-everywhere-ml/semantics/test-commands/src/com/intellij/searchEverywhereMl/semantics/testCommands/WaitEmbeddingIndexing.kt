package com.intellij.searchEverywhereMl.semantics.testCommands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.platform.ml.embeddings.search.services.EmbeddingIndexSettings
import com.intellij.platform.ml.embeddings.search.services.EmbeddingIndexSettingsImpl
import com.intellij.platform.ml.embeddings.search.services.FileBasedEmbeddingsManager
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import org.jetbrains.annotations.NonNls

class WaitEmbeddingIndexing(text: @NonNls String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "waitSemanticSearchIndexing"
    const val PREFIX = CMD_PREFIX + NAME
  }

  override suspend fun doExecute(context: PlaybackContext) {
    EmbeddingIndexSettingsImpl.getInstance().registerClientSettings(
      object : EmbeddingIndexSettings {
        override val shouldIndexFiles: Boolean = true
        override val shouldIndexClasses: Boolean = true
        override val shouldIndexSymbols: Boolean = true
      }
    )

    FileBasedEmbeddingsManager.getInstance(context.project).prepareForSearch().join()
  }

  override fun getName() = NAME
}
