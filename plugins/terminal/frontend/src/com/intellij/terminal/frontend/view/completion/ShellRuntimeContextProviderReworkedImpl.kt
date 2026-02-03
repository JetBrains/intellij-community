// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.completion

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.terminal.completion.ShellRuntimeContextProvider
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import org.jetbrains.plugins.terminal.block.completion.spec.EEL_DESCRIPTOR_KEY
import org.jetbrains.plugins.terminal.block.completion.spec.IS_REWORKED_KEY
import org.jetbrains.plugins.terminal.block.completion.spec.PROJECT_KEY
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellDataGeneratorProcessExecutorImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellFileSystemSupportImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellRuntimeContextImpl
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel

internal class ShellRuntimeContextProviderReworkedImpl(
  private val project: Project,
  private val sessionModel: TerminalSessionModel,
  private val envVariables: Map<String, String>,
  private val eelDescriptor: EelDescriptor,
) : ShellRuntimeContextProvider {
  private val generatorProcessExecutor = ShellDataGeneratorProcessExecutorImpl(eelDescriptor, envVariables)
  private val shellCommandExecutor = ShellCommandExecutorReworked(generatorProcessExecutor)
  private val fileSystemSupport = ShellFileSystemSupportImpl(eelDescriptor)

  override fun getContext(commandTokens: List<String>): ShellRuntimeContext {
    return ShellRuntimeContextImpl(
      currentDirectory = sessionModel.terminalState.value.currentDirectory ?: error("Current directory should be set at this moment"),
      envVariables = envVariables,
      commandTokens = commandTokens,
      definedShellName = null,
      generatorCommandsRunner = shellCommandExecutor,
      generatorProcessExecutor = generatorProcessExecutor,
      fileSystemSupport = fileSystemSupport,
    ).apply {
      putUserData(PROJECT_KEY, project)
      putUserData(IS_REWORKED_KEY, true)
      putUserData(EEL_DESCRIPTOR_KEY, eelDescriptor)
    }
  }
}