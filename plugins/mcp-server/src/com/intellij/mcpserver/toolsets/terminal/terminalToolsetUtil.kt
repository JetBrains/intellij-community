package com.intellij.mcpserver.toolsets.terminal

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.toolsets.terminal.TerminalToolset.CommandExecutionResult
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.wm.ToolWindow
import com.intellij.sh.run.ShConfigurationType
import com.intellij.terminal.TerminalExecutionConsole
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.execution.ParametersListUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.swing.JComponent
import kotlin.time.Duration

class CommandSession(val sessionId: String, val console: TerminalExecutionConsole)
val MCP_TERMINAL_KEY: Key<CommandSession> = Key.create("MCP_TERMINAL_KEY")

suspend fun executeShellCommand(window: ToolWindow?, project: Project, command: String, executeInShell: Boolean, sessionId: String?, timeout: Duration): CommandExecutionResult {
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
  val processHandler = object : ColoredProcessHandler(commandLine) {
    override fun getCommandLineForLog(): @NlsSafe String? = null
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
        val content = ContentFactory.getInstance().createContent(executionConsole.component, McpServerBundle.message("mcp.general.terminal.tab.name"), false)
        window.contentManager.addContent(content)
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
    exitCode.await()
  }
  if (exitCodeValue == null) {
    return CommandExecutionResult(is_timed_out = true, command_output = output.toString())
  }
  return CommandExecutionResult(command_exit_code = exitCodeValue, command_output = output.toString())
}

suspend fun askConfirmation(project: Project, command: String): Boolean {
  return withContext(Dispatchers.EDT) {
    //MessageDialogBuilder.yesNo("Approve command execution?", McpServerBundle.message("label.do.you.want.to.run.command.in.terminal", command.take(100)))
    //  .ask(pro)
    //Messages.showOkCancelDialog(project, ,
    //                            , "Approve", "Discard", )
    val confirmationDialog = object : DialogWrapper(project, true) {
      init {
        init()
        title = McpServerBundle.message("dialog.title.confirm.command.execution")
      }

      override fun createCenterPanel(): JComponent {
        return panel {
          row {
            label(McpServerBundle.message("label.do.you.want.to.run.command.in.terminal", command.take(100)))
          }
          row {
            comment(McpServerBundle.message("text.note.you.can.enable.brave.mode.in.settings.to.skip.this.confirmation"))
          }
        }
      }
    }
    confirmationDialog.show()
    return@withContext confirmationDialog.isOK
  }
}