// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.terminal.completion.ShellRuntimeContextProvider
import com.intellij.terminal.completion.spec.ShellCommandExecutor
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.util.PathUtil
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionScope
import org.jetbrains.plugins.terminal.block.completion.spec.PROJECT_KEY
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.block.session.ShellCommandListener
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil.toShellName
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptState
import org.jetbrains.plugins.terminal.block.session.CommandFinishedEvent

internal class ShellRuntimeContextProviderImpl(
  private val project: Project,
  private val session: BlockTerminalSession
) : ShellRuntimeContextProvider {

  val tracer = TelemetryManager.getTracer(TerminalCompletionScope)

  private val realGeneratorRunner: ShellCommandExecutor = ShellCommandExecutor { command ->
    tracer.spanBuilder("terminal-completion-run-generator-command")
      .setAttribute("terminal.command", command)
      .useWithScope {
      session.commandExecutionManager.runGeneratorAsync(command).await()
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

  override fun getContext(typedPrefix: String): ShellRuntimeContext {
    return ShellRuntimeContextImpl(
      curDirectory,
      typedPrefix,
      session.shellIntegration.shellType.toShellName(),
      generatorCommandsRunner
    ).apply {
      putUserData(PROJECT_KEY, project)
    }
  }

  companion object {
    val KEY: Key<ShellRuntimeContextProviderImpl> = Key.create("IJShellRuntimeContextProvider")
  }
}
