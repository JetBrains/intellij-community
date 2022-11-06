// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.performancePlugin.commands

import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageStateEventTracker
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.util.concurrent.CompletableFuture

internal class RecordStateCollectorsCommand(text: String, line: Int) : AbstractCommand(text, line) {
  override fun _execute(context: PlaybackContext): Promise<Any?> {
    val stateLogger = FUStateUsagesLogger.getInstance()
    return context.project.coroutineScope.async {
      coroutineScope {
        launch {
          stateLogger.logApplicationStates()
          stateLogger.logProjectStates(context.project)
        }

        for (extension in FeatureUsageStateEventTracker.EP_NAME.extensions) {
          launch {
            extension.reportNow()
          }
        }
      }
    }
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
