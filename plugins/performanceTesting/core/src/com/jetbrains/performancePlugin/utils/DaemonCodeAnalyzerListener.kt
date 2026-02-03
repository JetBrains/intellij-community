// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.utils

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.Ref
import com.intellij.util.messages.SimpleMessageBusConnection
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.future.asDeferred
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal object DaemonCodeAnalyzerListener {

  val LOG = logger<DaemonCodeAnalyzerListener>()

  /**
   * Listen to the SimpleMessageBusConnection to receive notifications when the daemon finishes.
   *
   * @param connection The SimpleMessageBusConnection to listen to.
   * @param spanRef A reference to a Span to end when the daemon finishes.
   * @param timeoutInSeconds timeout for waiting result, default without timeout.
   *
   * @return A DaemonCodeAnalyzerResult that completes when the daemon finishes.
   */
  fun listen(connection: SimpleMessageBusConnection,
             spanRef: Ref<Span>,
             timeoutInSeconds: Long = 0,
             expectedOpenedFile: String? = null): DaemonCodeAnalyzerResult {
    LOG.info("Start listening ${DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC.displayName}")
    val result = DaemonCodeAnalyzerResult(connection, spanRef, timeoutInSeconds)
    connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
      override fun daemonFinished(fileEditors: Collection<FileEditor>) {
        printFinishedAnalyzers(fileEditors)
        if (expectedOpenedFile == null) {
          result.release()
        }
        if (expectedOpenedFile != null && fileEditors.any { fileEditor -> fileEditor.file.name == expectedOpenedFile }) {
          result.release()
        }
      }
    })
    return result
  }

  private fun printFinishedAnalyzers(fileEditors: Collection<FileEditor>) {
    try {
      fileEditors.forEach { LOG.info("daemonFinished for ${it.file.name}") }
    } catch (throwable:Throwable) {
      LOG.error("printFinishedAnalyzers failed ${throwable.message}")
    }
  }

}

internal class DaemonCodeAnalyzerResult(private val connection: SimpleMessageBusConnection,
                                        private val spanRef: Ref<Span>,
                                        timeoutInSeconds: Long = 0) {
  private val job = CompletableFuture<Unit>()
  private var suppressErrors: Boolean = false
  private var errorMessage = "Timeout on waiting for demon code analyzer complete for $timeoutInSeconds seconds"

  init {
    if (timeoutInSeconds > 0) {
      job.orTimeout(timeoutInSeconds, TimeUnit.SECONDS)
    }
    job.exceptionally {
      release()
    }
  }

  fun isDone(): Boolean {
    return job.isDone
  }

  fun suppressErrors() {
    suppressErrors = true
  }

  fun withErrorMessage(message: String) {
    errorMessage = message
  }

  fun blockingWaitForComplete() {
    try {
      job.join()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      if (!suppressErrors)
        throw IllegalStateException(errorMessage, e)
    }
  }

  suspend fun waitForComplete() {
    try {
      job.asDeferred().join()
    }
    catch (e: Exception) {
      if (!suppressErrors)
        throw IllegalStateException(errorMessage, e)
    }
  }

  fun release() {
    try {
      connection.disconnect()
    }
    finally {
      spanRef.get()?.end()
      if (!job.isDone && !job.isCancelled) {
        job.complete(Unit)
      }
    }
  }

  fun onError(action: (e: Throwable) -> Unit) {
    job.exceptionally(action::invoke)
  }
}