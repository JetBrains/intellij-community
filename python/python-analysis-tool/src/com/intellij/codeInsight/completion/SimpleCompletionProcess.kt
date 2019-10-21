package com.intellij.codeInsight.completion

class SimpleCompletionProcess: CompletionProcess {
  override fun isAutopopupCompletion(): Boolean = false

  companion object {
    val INSTANCE = SimpleCompletionProcess()
  }
}