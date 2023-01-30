package com.jetbrains.performancePlugin.utils

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.util.Ref
import com.intellij.util.messages.SimpleMessageBusConnection
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Scope
import java.util.concurrent.CompletableFuture

object DaemonCodeAnalyzerListener {

  /**
   * Listen to the SimpleMessageBusConnection to receive notifications when the daemon finishes.
   *
   * @param connection The SimpleMessageBusConnection to listen to.
   * @param spanRef A reference to a Span to end when the daemon finishes.
   * @param scopeRef A reference to a Scope to close when the daemon finishes.
   *
   * @return A CompletableFuture that completes when the daemon finishes.
   */
  fun listen(connection: SimpleMessageBusConnection, spanRef: Ref<Span>, scopeRef: Ref<Scope>): CompletableFuture<Unit> {
    val job = CompletableFuture<Unit>()
    connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
      override fun daemonFinished() {
        try {
          connection.disconnect()
        }
        finally {
          spanRef.get()?.end()
          scopeRef.get()?.close()
          job.complete(Unit)
        }
      }
    })
    return job
  }
}