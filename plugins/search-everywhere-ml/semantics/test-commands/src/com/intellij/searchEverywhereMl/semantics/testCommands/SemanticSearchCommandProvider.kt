package com.intellij.searchEverywhereMl.semantics.testCommands

import com.jetbrains.performancePlugin.CommandProvider
import com.jetbrains.performancePlugin.CreateCommand

class SemanticSearchCommandProvider: CommandProvider {
  override fun getCommands(): MutableMap<String, CreateCommand> {
    return mutableMapOf(
      WaitEmbeddingIndexing.PREFIX to CreateCommand(::WaitEmbeddingIndexing),
    )
  }
}
