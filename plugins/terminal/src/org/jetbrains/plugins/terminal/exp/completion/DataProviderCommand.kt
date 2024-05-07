// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import org.jetbrains.plugins.terminal.exp.BlockTerminalSession

/**
 * Sometimes we need to implicitly (without visual appearance) execute a command in current shell
 * to get some data (for instance - list of files in current directory)
 * This could be executed only between blocks and never during current block running.
 * This class relates to "Generator" term.
 *
 * @see org.jetbrains.plugins.terminal.exp.ShellCommandExecutionManager.Generator
 */
internal interface DataProviderCommand<T> {
  val functionName: String
  val parameters: List<String>
  val defaultResult: T

  fun isAvailable(session: BlockTerminalSession): Boolean
  fun parseResult(result: String): T
}
