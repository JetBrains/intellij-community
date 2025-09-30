package com.intellij.mcpserver.toolsets.terminal

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.mcpserver.clientInfo
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.toolsets.terminal.TerminalToolset.CommandExecutionResult
import com.intellij.mcpserver.util.TruncateMode
import com.intellij.mcpserver.util.truncateText
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.wm.ToolWindow
import com.intellij.sh.run.ShConfigurationType
import com.intellij.terminal.TerminalExecutionConsole
import com.intellij.ui.content.ContentFactory
import com.intellij.util.execution.ParametersListUtil
import kotlinx.coroutines.*
import kotlin.time.Duration

class CommandSession(val sessionId: String, val console: TerminalExecutionConsole)
val MCP_TERMINAL_KEY: Key<CommandSession> = Key.create("MCP_TERMINAL_KEY")

suspend fun executeShellCommand(
  window: ToolWindow?,
  project: Project,
  command: String,
  executeInShell: Boolean,
  sessionId: String?,
  timeout: Duration,
  maxLinesCount: Int,
  truncateMode: TruncateMode = TruncateMode.START,
): CommandExecutionResult {
  val defaultShell = ShConfigurationType.getDefaultShell(project)

  val commandLine = if (executeInShell) {
    GeneralCommandLine(defaultShell, "-c", command)
  }
  else {
    val parameters = ParametersListUtil.parse(command)
    GeneralCommandLine(parameters)
  }

  commandLine.withWorkingDirectory(project.basePath?.toNioPathOrNull())

  // to start in a real terminal emulator pass PtyCommandLine(commandLine), but it works badly now
  val processHandler = try {
    object : ColoredProcessHandler(commandLine) {
      override fun getCommandLineForLog(): @NlsSafe String? = null
    }
  }
  catch (e: ExecutionException) {
    mcpFail("Can't execute command line '${commandLine.commandLineString}': ${e.message}")
  }

  val output = StringBuilder()
  val exitCode = CompletableDeferred<Int>()
  processHandler.addProcessListener(object : ProcessListener {
    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
      if (outputType == ProcessOutputTypes.SYSTEM) return
      output.append(event.text)
    }

    override fun processTerminated(event: ProcessEvent) {
      exitCode.complete(event.exitCode)
    }
  })

  if (window != null) {
    withContext(Dispatchers.EDT) {
      val existing = if (sessionId != null) {
        window.contentManager.contents.find { it.getUserData(MCP_TERMINAL_KEY)?.sessionId == sessionId }
      }
      else null
      if (existing != null) {
        existing.getUserData(MCP_TERMINAL_KEY)!!.console.attachToProcess(processHandler)
        window.contentManager.setSelectedContent(existing)
      }
      else {
        val executionConsole = TerminalExecutionConsole(project, processHandler).withConvertLfToCrlfForNonPtyProcess(true)
        @Suppress("HardCodedStringLiteral")
        val displayName = currentCoroutineContext().clientInfo.name
        val content = ContentFactory.getInstance().createContent(executionConsole.component, displayName, false)
        window.contentManager.addContent(content)
        Disposer.register(content) {
          @Suppress("HardCodedStringLiteral") // visible to LLM only
          exitCode.completeExceptionally(ExecutionException("Terminal tab closed by user"))
          processHandler.destroyProcess()
        }
        if (sessionId != null) {
          content.putUserData(MCP_TERMINAL_KEY, CommandSession(sessionId, executionConsole))
        }
        window.contentManager.setSelectedContent(content)
      }
      window.activate(null)
    }
  }

  val shellHeader = if (executeInShell) "$defaultShell$ $command" else "$ $command"
  processHandler.notifyTextAvailable("$shellHeader\n", ProcessOutputTypes.SYSTEM)

  processHandler.startNotify()

  val exitCodeValue = withTimeoutOrNull(timeout) {
    try {
      exitCode.await()
    }
    catch (e: ExecutionException) {
      mcpFail("Execution failed: ${e.message}")
    }
  }
  val truncateText = truncateText(text = output.toString(), maxLinesCount = maxLinesCount, truncateMode = truncateMode)
  if (exitCodeValue == null) {
    return CommandExecutionResult(is_timed_out = true, command_output = truncateText)
  }
  return CommandExecutionResult(command_exit_code = exitCodeValue, command_output = truncateText)
}