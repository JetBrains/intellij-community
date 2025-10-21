// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.completion

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.terminal.completion.ShellRuntimeContextProvider
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil.toShellName
import org.jetbrains.plugins.terminal.block.completion.spec.IS_REWORKED_KEY
import org.jetbrains.plugins.terminal.block.completion.spec.PROJECT_KEY
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellRuntimeContextImpl
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.util.ShellType

internal class ShellRuntimeContextProviderReworkedImpl(
  private val project: Project,
  private val sessionModel: TerminalSessionModel,
  eelDescriptor: EelDescriptor,
) : ShellRuntimeContextProvider {
  private val shellCommandExecutor = ShellCommandExecutorReworked(eelDescriptor)
  private val fileSystemSupport = ShellFileSystemSupportImpl(eelDescriptor)

  override fun getContext(typedPrefix: String): ShellRuntimeContext {
    return ShellRuntimeContextImpl(
      sessionModel.terminalState.value.currentDirectory,
      typedPrefix,
      ShellType.ZSH.toShellName(),
      shellCommandExecutor,
      fileSystemSupport,
    ).apply {
      putUserData(PROJECT_KEY, project)
      putUserData(IS_REWORKED_KEY, true)
    }
  }
}