// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.execution.DefaultExecutionTarget
import com.intellij.execution.ExecutionManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunManagerImpl.Companion.getInstanceImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Ref
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NonNls
import java.util.concurrent.TimeUnit

/**
 *   Command debug specified configuration if configuration exists and list of breakpoint is not empty.
 *   Command finished when debug process stopped on first breakpoint or by timeout if timeout was set.
 *   Example: %runConfiguration IDEA
 *   Example: %runConfiguration IDEA, 60
 */
class DebugRunConfigurationCommand(text: String, line: Int) : AbstractCallbackBasedCommand(text, line, true) {

  override fun execute(callback: ActionCallback, context: PlaybackContext) {
    val arguments = extractCommandList(PREFIX, ",")
    if (arguments.isEmpty()) {
      callback.reject("Usage %debugStep &lt;configuration_name&gt; [&lt;timeout_in_seconds&gt;]")
      return
    }
    val configurationNameToRun = arguments[0]
    var timeoutInSeconds: Long? = null
    if (arguments.size > 1) timeoutInSeconds = arguments[1].toLong()

    val breakpointManager = XDebuggerManager.getInstance(context.project).breakpointManager
    val nonDefaultBreakpoints = breakpointManager.allBreakpoints.filter { !breakpointManager.isDefaultBreakpoint(it) }
    if (nonDefaultBreakpoints.isEmpty()) {
      callback.reject("Breakpoint for this project were not found")
      return
    }
    val runManager: RunManagerImpl = getInstanceImpl(context.project)
    val configurationToRun = RunConfigurationCommand.getConfigurationByName(runManager, configurationNameToRun)
    if (configurationToRun == null) {
      callback.reject("Specified configuration was not found: $configurationNameToRun")
      return
    }

    val spanBuilder = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).setParent(PerformanceTestSpan.getContext())
    val spanRef = Ref<Span>()
    val scopeRef = Ref<Scope>()

    val connection = context.project.messageBus.connect()
    var job: Job? = null
    connection.subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
      override fun processStarted(debugProcess: XDebugProcess) {
        if (timeoutInSeconds != null) {
          job = CoroutineScope(Dispatchers.IO).launch {
            if (!Waiter.checkCondition { callback.isDone || callback.isRejected }.await(timeoutInSeconds, TimeUnit.SECONDS)) {
              callback.reject(
                "Debug run configuration $configurationNameToRun was started $timeoutInSeconds seconds ago but was not paused by any breakpoint yet")
              connection.disconnect()
            }
          }
        }

        debugProcess.session.addSessionListener(object : XDebugSessionListener {
          override fun sessionPaused() {
            super.sessionPaused()
            callback.setDone()
            spanRef.get().end()
            scopeRef.get().close()
            connection.disconnect()
            job?.cancel()
          }
        })
      }
    })

    ApplicationManager.getApplication().runWriteAction(Context.current().wrap(Runnable {
      val runnerAndConfigurationSettings = RunnerAndConfigurationSettingsImpl(runManager, configurationToRun)
      spanRef.set(spanBuilder.startSpan())
      scopeRef.set(spanRef.get().makeCurrent())
      ExecutionManager.getInstance(context.project).restartRunProfile(context.project, DefaultDebugExecutor(),
                                                                      DefaultExecutionTarget.INSTANCE, runnerAndConfigurationSettings,
                                                                      null)
    }))
  }

  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "debugRunConfiguration"
    const val SPAN_NAME: @NonNls String = "debugRunConfiguration"
  }
}