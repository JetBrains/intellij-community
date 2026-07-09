package com.intellij.terminal.frontend.toolwindow

import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.startup.TerminalProcessType

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TerminalRequestedProcessOptions {
  val shellCommand: List<String>?
  val workingDirectory: @MultiRoutingFileSystemPath String?
  val envVariables: Map<String, String>
  val processType: TerminalProcessType
}