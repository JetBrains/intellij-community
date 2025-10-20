// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.session

import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalStartupOptions {
  val shellCommand: List<String>
  val workingDirectory: String
  val envVariables: Map<String, String>
}

@ApiStatus.Experimental
fun TerminalStartupOptions.getShellName(): ShellName {
  val executablePath = shellCommand.first() // it should be guaranteed that it is not empty
  val executableName = PathUtil.getFileName(executablePath)
  return ShellName.of(executableName)
}