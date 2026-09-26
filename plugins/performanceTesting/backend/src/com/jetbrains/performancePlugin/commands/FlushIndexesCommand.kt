// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise

class FlushIndexesCommand(text: String, line: Int) : AbstractCommand(text, line) {
  companion object {
    const val PREFIX = CMD_PREFIX + "flushIndexes"
    private val LOG = logger<FlushIndexesCommand>()
  }

  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val actionCallback = ActionCallbackProfilerStopper()
    val project = context.project
    DumbService.getInstance(project).smartInvokeLater {
      object : Task.Modal(project, PerformanceTestingBundle.message("flushing.indexes"), false) {
        override fun run(indicator: ProgressIndicator) {
          try {
            runReadAction { (FileBasedIndex.getInstance() as FileBasedIndexImpl).flushIndexes() }
            LOG.info("Indexing finished")
            actionCallback.setDone()
          }
          catch (e: Throwable) {
            actionCallback.reject(e.message)
          }
        }
      }.queue()
    }
    return actionCallback.toPromise()
  }
}