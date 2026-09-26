package com.intellij.searchEverywhereLucene.performanceTesting

import com.jetbrains.performancePlugin.CommandProvider
import com.jetbrains.performancePlugin.CreateCommand

internal class LuceneTestCommandProvider : CommandProvider {
  override fun getCommands(): MutableMap<String, CreateCommand> {
    return mutableMapOf(
      WaitLuceneIndexing.PREFIX to CreateCommand(function = ::WaitLuceneIndexing),
    )
  }
}