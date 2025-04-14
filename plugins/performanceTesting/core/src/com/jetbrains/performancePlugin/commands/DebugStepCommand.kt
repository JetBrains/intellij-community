// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Ref
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import org.jetbrains.annotations.NonNls

/**
 * Command to debug step OVER/IN/INTO in debug process.
 * Example: %debugStep OVER
 */
class DebugStepCommand(text: String, line: Int) : AbstractCallbackBasedCommand(text, line, true) {
  override fun execute(callback: ActionCallback, context: PlaybackContext) {
    val debugStepType: DebugStepTypes
    try {
      debugStepType = DebugStepTypes.valueOf(extractCommandArgument(PREFIX))
    }
    catch (e: IllegalArgumentException) {
      callback.reject("Unknown special character. Please use: OVER, INTO or OUT")
      return
    }

    val debugSessions = XDebuggerManager.getInstance(context.project).debugSessions
    val spanBuilder = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME + "_" + debugStepType.name.lowercase()).setParent(
      PerformanceTestSpan.getContext())
    val spanRef = Ref<Span>()
    val scopeRef = Ref<Scope>()

    if (debugSessions.isEmpty()) {
      callback.reject("Debug process was not started")
      return
    }
    if (debugSessions.size > 1) {
      callback.reject("Currently running ${debugSessions.size} debug processes")
      return
    }

    val debugSession = debugSessions.first()
    debugSession.debugProcess.session.addSessionListener(object : XDebugSessionListener {
      override fun sessionPaused() {
        super.sessionPaused()
        callback.setDone()
        spanRef.get().end()
        scopeRef.get().close()
      }
    })

    ApplicationManager.getApplication().runWriteAction(Context.current().wrap(Runnable {
      spanRef.set(spanBuilder.startSpan())
      scopeRef.set(spanRef.get().makeCurrent())
      when (debugStepType) {
        DebugStepTypes.OVER -> debugSession.stepOver(false)
        DebugStepTypes.INTO -> debugSession.stepInto()
        DebugStepTypes.OUT -> debugSession.stepOut()
      }
    }))
  }

  companion object {
    private enum class DebugStepTypes { OVER, INTO, OUT }

    const val PREFIX: @NonNls String = CMD_PREFIX + "debugStep"
    const val SPAN_NAME: @NonNls String = "debugStep"
  }
}