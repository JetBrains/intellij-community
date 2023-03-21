package com.jetbrains.performancePlugin.commands

import com.intellij.execution.DefaultExecutionTarget
import com.intellij.execution.ExecutionManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunManagerImpl.Companion.getInstanceImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.util.concurrent.TimeUnit

class DebugRunConfigurationCommand(text: String, line: Int) : AbstractCallbackBasedCommand(text, line, true) {

  override fun execute(callback: ActionCallback, context: PlaybackContext) {
    val arguments = extractCommandList(PREFIX, ",")
    val configurationNameToRun = arguments[0]
    val timeoutInSeconds = arguments[1].toLong()

    val breakpointManager = XDebuggerManager.getInstance(context.project).breakpointManager
    val notDefaultBreakpoints = breakpointManager.allBreakpoints.filter { !breakpointManager.isDefaultBreakpoint(it) }
    if (notDefaultBreakpoints.isEmpty()) {
      callback.reject("Breakpoint for this project were not found")
      return
    }
    val runManager: RunManagerImpl = getInstanceImpl(context.project)
    val configurationToRun = RunConfigurationCommand.getConfigurationByName(runManager, configurationNameToRun)
    if (configurationToRun == null) {
      callback.reject("Specified configuration was not found: $configurationNameToRun")
      return
    }

    val connection = context.project.messageBus.connect()
    var job: Job
    connection.subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
      override fun processStarted(debugProcess: XDebugProcess) {
        job = CoroutineScope(Dispatchers.IO).launch {
          if (!Waiter.checkCondition { callback.isDone || callback.isRejected }.await(timeoutInSeconds, TimeUnit.SECONDS)) {
            callback.reject("Debug run configuration $configurationNameToRun was started $timeoutInSeconds seconds ago but was not paused by any breakpoint yet")
            connection.disconnect()
          }
        }

        debugProcess.session.addSessionListener(object : XDebugSessionListener {
          override fun sessionPaused() {
            super.sessionPaused()
            callback.setDone()
            connection.disconnect()
            job.cancel()
          }
        })
      }
    })

    WriteAction.runAndWait<IOException> {
      val executor = DefaultDebugExecutor()
      val target = DefaultExecutionTarget.INSTANCE
      val runnerAndConfigurationSettings = RunnerAndConfigurationSettingsImpl(runManager, configurationToRun)
      ExecutionManager.getInstance(context.project).restartRunProfile(context.project, executor, target, runnerAndConfigurationSettings,
                                                                      null)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(SetBreakpointCommand::class.java)
    const val PREFIX: @NonNls String = CMD_PREFIX + "debugRunConfiguration"
  }
}