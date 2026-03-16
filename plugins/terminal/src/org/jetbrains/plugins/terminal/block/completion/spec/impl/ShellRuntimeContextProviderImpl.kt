// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.openapi.project.Project
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.terminal.completion.ShellRuntimeContextProvider
import com.intellij.terminal.completion.spec.ShellCommandExecutor
import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionScope
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil.toShellName
import org.jetbrains.plugins.terminal.block.completion.spec.PROJECT_KEY
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptState
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.block.session.CommandFinishedEvent
import org.jetbrains.plugins.terminal.block.session.ShellCommandListener

@ApiStatus.Internal
class ShellRuntimeContextProviderImpl(
  private val project: Project,
  private val session: BlockTerminalSession,
) : ShellRuntimeContextProvider {

  private val tracer = TelemetryManager.getTracer(TerminalCompletionScope)

  private val realGeneratorRunner: ShellCommandExecutor = object : ShellCommandExecutor {
    override suspend fun runShellCommand(directory: String, command: String): ShellCommandResult {
      return tracer.spanBuilder("terminal-completion-run-generator-command")
        .setAttribute("terminal.command", command)
        .useWithScope {
          session.commandExecutionManager.runGeneratorAsync(command).await()
        }
    }
  }

  private val generatorCommandsRunner: ShellCachingGeneratorCommandsRunner = ShellCachingGeneratorCommandsRunner(realGeneratorRunner)

  @Volatile
  private var curDirectory: String = ""

  init {
    session.addCommandListener(object : ShellCommandListener {
      override fun promptStateUpdated(newState: TerminalPromptState) {
        curDirectory = PathUtil.toSystemIndependentName(newState.currentDirectory)
      }

      override fun commandFinished(event: CommandFinishedEvent) {
        generatorCommandsRunner.reset()
      }
    })
  }

  override fun getContext(commandTokens: List<String>): ShellRuntimeContext {
    return ShellRuntimeContextImpl(
      currentDirectory = curDirectory,
      envVariables = emptyMap(),
      commandTokens = commandTokens,
      definedShellName = session.shellIntegration.shellType.toShellName(),
      generatorCommandsRunner = generatorCommandsRunner
    ).apply {
      putUserData(PROJECT_KEY, project)
    }
  }
}
