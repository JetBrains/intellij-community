// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.openapi.util.Key
import com.intellij.terminal.block.completion.ShellRuntimeContextProvider
import com.intellij.terminal.block.completion.spec.ShellName
import com.intellij.terminal.block.completion.spec.ShellRuntimeContext
import com.intellij.util.PathUtil
import org.jetbrains.plugins.terminal.exp.BlockTerminalSession
import org.jetbrains.plugins.terminal.exp.ShellCommandListener
import org.jetbrains.plugins.terminal.exp.prompt.TerminalPromptState
import org.jetbrains.plugins.terminal.util.ShellType

internal class IJShellRuntimeContextProvider(private val session: BlockTerminalSession) : ShellRuntimeContextProvider {
  private val generatorCommandsRunner: ShellGeneratorCommandsRunner = ShellGeneratorCommandsRunner(session)

  @Volatile
  private var curDirectory: String = ""

  init {
    session.addCommandListener(object : ShellCommandListener {
      override fun promptStateUpdated(newState: TerminalPromptState) {
        curDirectory = PathUtil.toSystemIndependentName(newState.currentDirectory)
      }
    })
  }

  override fun getContext(commandText: String, typedPrefix: String): ShellRuntimeContext {
    return IJShellRuntimeContext(
      curDirectory,
      commandText,
      typedPrefix,
      session.shellIntegration.shellType.toShellName(),
      generatorCommandsRunner
    )
  }

  private fun ShellType.toShellName(): ShellName {
    return ShellName(this.toString().lowercase())
  }

  companion object {
    val KEY: Key<IJShellRuntimeContextProvider> = Key.create("IJShellRuntimeContextProvider")
  }
}