// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.performancePlugin.commands

import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.ProjectFUStateUsagesLogger
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.util.concurrent.CompletableFuture

internal class RecordStateCollectorsCommand(text: String, line: Int) : AbstractCommand(text, line) {
  override fun _execute(context: PlaybackContext): Promise<Any?> {
    return context.project.service<ProjectFUStateUsagesLogger>()
      .scheduleLogApplicationAndProjectState()
      .asCompletableFuture()
      .thenCompose { FeatureUsageLogger.flush() }
      .toPromise()
  }

  companion object {
    const val PREFIX = CMD_PREFIX + "recordStateCollectors"
  }

}

internal fun CompletableFuture<Void>.toPromise(): Promise<Any?> {
  val promise = AsyncPromise<Any?>()
  thenApply { promise.setResult(null) }
    .exceptionally { throwable -> promise.setError(throwable) }
  return promise
}
