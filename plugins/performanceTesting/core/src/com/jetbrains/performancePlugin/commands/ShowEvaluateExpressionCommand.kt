// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.platform.diagnostic.telemetry.IJNoopTracer
import com.intellij.util.io.await
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import com.intellij.xdebugger.impl.evaluate.XDebuggerEvaluationDialog
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.utils.parse
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture

class ShowEvaluateExpressionCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val SPAN_NAME: String = "evaluateExpression"
    const val PREFIX: String = CMD_PREFIX + "showEvaluateExpression"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val options = EvaluateExpressionArguments()
    options.parse(extractCommandArgument(PREFIX))
    withContext(Dispatchers.EDT) {
      val debugSessions = XDebuggerManager.getInstance(context.project).debugSessions
      if (debugSessions.isEmpty()) throw IllegalStateException("Debug process was not started")
      if (debugSessions.size > 1) throw IllegalStateException("Currently running ${debugSessions.size} debug processes")
      val debugSession = debugSessions.first()
      val editorsProvider = debugSession.debugProcess.editorsProvider
      val dialog = XDebuggerEvaluationDialog(debugSession, editorsProvider, XExpressionImpl(options.expression, null, null),
                                             debugSession.currentPosition, false)
      dialog.show()
      repeat(options.performEvaluateCount) {
        startEvaluation(dialog)
      }
    }
  }

  private suspend fun startEvaluation(dialog: XDebuggerEvaluationDialog) {
    val tracer: Tracer = if (isWarmupMode()) IJNoopTracer else PerformanceTestSpan.TRACER
    val startSpan = tracer.spanBuilder(SPAN_NAME).startSpan()
    val future = CompletableFuture<Any>()
    val callback = object : XDebuggerEvaluator.XEvaluationCallback {
      override fun errorOccurred(errorMessage: String) {
        startSpan.end()
        future.completeExceptionally(IllegalStateException("Error occurred: $errorMessage"))
      }

      override fun evaluated(result: XValue) {
        startSpan.end()
        future.complete("")
      }
    }
    dialog.startEvaluation(callback)
    future.await()
  }

  private fun isWarmupMode(): Boolean {
    return text.contains("WARMUP")
  }
}