// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.commandInterfaceConsole

import com.intellij.execution.console.LanguageConsoleBuilder
import com.intellij.execution.console.LanguageConsoleView
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.ui.content.Content
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.commandInterface.command.Command
import com.intellij.commandInterface.command.CommandExecutor
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory

/**
 * Displays command-line console for user.
 * Start with ``createConsole``.
 * @author Ilya.Kazakevich
 */

/**
 * Information to be provided to console.
 * With out of it console is dumb and is not able to execute any command by itself
 * (you would need [LanguageConsoleBuilder.registerExecuteAction])).
 *
 */
data class CommandsInfo(
  /**
   * Commands available for this console.
   * You may provide null, but no suggestion nor special execution will work.
   */
  val commands: List<Command>?,
  /**
   * Default executor to run commands. Always used if commands are null, or only if command is unknown in other case.
   * In case of null use [LanguageConsoleBuilder.registerExecuteAction] or only provided commands will be supported.
   */
  val unknownCommandsExecutor: CommandExecutor?,
  /**
   * Each command stdout may be redirected to optional filter.
   * Filter can do what ever it wants, but should return text to display to user. Empty text is ok, but not null
   */
  val outputFilter: ((String) -> String)?
)

/**
 * Counterpart of jb_escape_output function on python side. Messages with this prefix are considered to be escape sequence by [jbFilter]
 */
private const val _JB_PREFIX = "##[jetbrains"

private val LANGUAGE_CONSOLE_VIEW_KEY = Key<LanguageConsoleView>("LanguageConsoleView")


/**
 * Counterpart of jb_escape_output.
 * If you write filter for [CommandsInfo], you may need to unescape test first.
 * This function unescapes escaped text and provides it to [filter]
 */
fun jbFilter(filter: (String) -> String): (String) -> String {
  return { s -> if (s.startsWith(_JB_PREFIX)) (filter.invoke(s.substringAfter(_JB_PREFIX).trim()) + "\n").trim() else s }
}

/**
 * Returns [LanguageConsoleView] if is already present in the toolwindow, null otherwise.
 */
@RequiresEdt
fun findAndOpenExistingConsoleIfExists(
  module: Module,
  @Nls consoleName: String,
  requestFocus: Boolean = true
): LanguageConsoleView? {
  val project = module.project
  val toolWindow = getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return null
  val contentManager = toolWindow.contentManager

  val content = contentManager.findContent(consoleName) ?: return null
  val languageConsoleView = content.getUserData(LANGUAGE_CONSOLE_VIEW_KEY)

  openContentInTerminal(toolWindow, content, requestFocus)

  return languageConsoleView
}

/**
 * Creates and displays command-line console for user.
 * The console will be displayed in the terminal.
 *
 * @param module        module to display console for.
 * @param consoleName   Console name (would be used in prompt, history etc)
 * @param prompt        default console prompt (or console name if not provided)
 * @param commandsInfo  commands, executor and other stuff (see [CommandsInfo]).
 *                      Might be null with same effect as [CommandsInfo] with all fields are null.
 * @param requestFocus  request focus.
 *
 * @return newly created console. You do not need to do anything with this value to display console: it will be displayed automatically
 */
@RequiresEdt
fun createConsoleAndOpen(
  module: Module,
  @Nls consoleName: String,
  @Nls prompt: String = consoleName,
  commandsInfo: CommandsInfo?,
  requestFocus: Boolean = true
): LanguageConsoleView? {
  val project = module.project
  val toolWindow: ToolWindow = getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return null
  val contentManager = toolWindow.contentManager

  val console = CommandConsole.createConsole(module, prompt, commandsInfo)

  val panel = PanelWithActions.createConsolePanelWithActions(
    console,
    console.editor.component,
    null
  )
  ArgumentHintLayer.attach(console)

  val content = ContentImpl(panel, consoleName, true)
  contentManager.addContent(content)

  openContentInTerminal(toolWindow, content, requestFocus)

  content.putUserData(LANGUAGE_CONSOLE_VIEW_KEY, console)

  return console
}

private fun openContentInTerminal(
  toolWindow: ToolWindow,
  content: Content,
  requestFocus: Boolean = true
) {
  val contentManager = toolWindow.contentManager

  val selectRunnable = Runnable {
    contentManager.setSelectedContent(content, requestFocus)
  }
  toolWindow.activate(selectRunnable, true, true)
}
