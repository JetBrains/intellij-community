package com.intellij.searchEverywhereMl.semantics.testCommands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.platform.ml.embeddings.indexer.FileBasedEmbeddingIndexer
import com.intellij.platform.ml.embeddings.settings.EmbeddingIndexSettings
import com.intellij.platform.ml.embeddings.settings.EmbeddingIndexSettingsImpl
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

    FileBasedEmbeddingIndexer.getInstance().prepareForSearch(context.project).join()
  }

  override fun getName() = NAME
}
