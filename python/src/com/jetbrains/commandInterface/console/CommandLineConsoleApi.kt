/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.commandInterface.console

import com.intellij.execution.console.LanguageConsoleBuilder
import com.intellij.execution.console.LanguageConsoleView
import com.intellij.openapi.module.Module
import com.jetbrains.commandInterface.command.Command
import com.jetbrains.commandInterface.command.CommandExecutor
import com.jetbrains.toolWindowWithActions.WindowWithActions

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

/**
 * Counterpart of jb_escape_output.
 * If you write filter for [CommandsInfo], you may need to unescape test first.
 * This function unescapes escaped text and provides it to [filter]
 */
fun jbFilter(filter: (String) -> String): (String) -> String {
  return { s -> if (s.startsWith(_JB_PREFIX)) (filter.invoke(s.substringAfter(_JB_PREFIX).trim()) + "\n").trim() else s }
}

/**
 * Creates and displays command-line console for user.

 * @param module                     module to display console for.
 * @param consoleName                Console name (would be used in prompt, history etc)
 * @param commandsInfo  commands, executor and other stuff (see [CommandsInfo]).
 *                                    May be null with same effect as [CommandsInfo] with all fields are null.
 * @param prompt default console prompt (or console name if not provided)
 *
 * @return newly created console. You do not need to do anything with this value to display console: it will be displayed automatically
 */
fun createConsole(
  module: Module,
  consoleName: String,
  prompt: String  = consoleName,
  commandsInfo: CommandsInfo?): LanguageConsoleView {
  val project = module.project
  val console = CommandConsole.createConsole(module, prompt, commandsInfo)


  // Show console on "toolwindow"
  WindowWithActions.showConsoleWithProcess(console,
                                           console.editor.component,
                                           consoleName,
                                           project,
                                           null)

  ArgumentHintLayer.attach(console) // Display [arguments]
  return console
}


