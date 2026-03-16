// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.testFramework.completion.impl

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.terminal.completion.ShellRuntimeContextProvider
import com.intellij.terminal.completion.spec.ShellCommandExecutor
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.completion.spec.EEL_DESCRIPTOR_KEY
import org.jetbrains.plugins.terminal.block.completion.spec.IS_REWORKED_KEY
import org.jetbrains.plugins.terminal.block.completion.spec.PROJECT_KEY
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGeneratorProcessExecutor
import org.jetbrains.plugins.terminal.block.completion.spec.ShellFileSystemSupport
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellRuntimeContextImpl

@ApiStatus.Internal
class TestRuntimeContextProvider(
  private val project: Project? = null,
  private val directory: String = "",
  private val envVariables: Map<String, String> = emptyMap(),
  private val isReworkedTerminal: Boolean = true,
  private val generatorCommandsRunner: ShellCommandExecutor = DummyShellCommandExecutor,
  private val generatorProcessExecutor: ShellDataGeneratorProcessExecutor? = null,
  private val fileSystemSupport: ShellFileSystemSupport? = null,
) : ShellRuntimeContextProvider {
  override fun getContext(commandTokens: List<String>): ShellRuntimeContext {
    return ShellRuntimeContextImpl(
      currentDirectory = directory,
      envVariables = envVariables,
      commandTokens = commandTokens,
      definedShellName = null,
      generatorCommandsRunner = generatorCommandsRunner,
      generatorProcessExecutor = generatorProcessExecutor,
      fileSystemSupport = fileSystemSupport,
    ).also { context ->
      context.putUserData(PROJECT_KEY, project)
      if (isReworkedTerminal) {
        context.putUserData(IS_REWORKED_KEY, true)
      }
      project?.getEelDescriptor()?.let {
        context.putUserData(EEL_DESCRIPTOR_KEY, it)
      }
    }
  }
}