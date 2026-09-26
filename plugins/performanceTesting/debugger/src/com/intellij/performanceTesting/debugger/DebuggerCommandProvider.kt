// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.debugger

import com.jetbrains.performancePlugin.CommandProvider
import com.jetbrains.performancePlugin.CreateCommand

internal class DebuggerCommandProvider : CommandProvider {
  override fun getCommands(): Map<String, CreateCommand> = mapOf(
    SetBreakpointCommand.PREFIX to CreateCommand(::SetBreakpointCommand),
    DebugRunConfigurationCommand.PREFIX to CreateCommand(::DebugRunConfigurationCommand),
    DebugStepCommand.PREFIX to CreateCommand(::DebugStepCommand),
    StopDebugProcessCommand.PREFIX to CreateCommand(::StopDebugProcessCommand),
    ShowEvaluateExpressionCommand.PREFIX to CreateCommand(::ShowEvaluateExpressionCommand),
    RemoveBreakpointCommand.PREFIX to CreateCommand(::RemoveBreakpointCommand),
    DebugToggleBreakpointCommand.PREFIX to CreateCommand(::DebugToggleBreakpointCommand),
    WaitForDebugSessionsEndCommand.PREFIX to CreateCommand(::WaitForDebugSessionsEndCommand),
  )
}
