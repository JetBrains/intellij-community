package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import org.jetbrains.plugins.terminal.startup.TerminalProcessType

/**
 * The requested process options a terminal tab was created with.
 */
internal data class TerminalRequestedProcessOptions(
  val shellCommand: List<String>?,
  val workingDirectory: @MultiRoutingFileSystemPath String?,
  val envVariables: Map<String, String>,
  val processType: TerminalProcessType,
)