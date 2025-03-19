package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise

abstract class PerformanceCommandCoroutineAdapter(text: String, line: Int) : PerformanceCommand(text, line) {
  protected abstract suspend fun doExecute(context: PlaybackContext)

  override fun isToDumpCommand() = false

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    @Suppress("UNCHECKED_CAST")
    return object : PlaybackCommandCoroutineAdapter(text, line) {
      override suspend fun doExecute(context: PlaybackContext) {
        this@PerformanceCommandCoroutineAdapter.doExecute(context)
      }
    }.execute(context).asPromise() as Promise<Any?>
  }
}