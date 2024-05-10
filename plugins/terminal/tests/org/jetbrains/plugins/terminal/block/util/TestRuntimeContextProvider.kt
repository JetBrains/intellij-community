// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.util

import com.intellij.terminal.block.completion.ShellRuntimeContextProvider
import com.intellij.terminal.block.completion.spec.ShellName
import com.intellij.terminal.block.completion.spec.ShellRuntimeContext
import org.jetbrains.plugins.terminal.block.completion.spec.impl.IJShellRuntimeContext
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellGeneratorCommandsRunner

internal class TestRuntimeContextProvider(
  private val directory: String = "",
  private val shellName: ShellName = ShellName("dummy"),
  private val generatorCommandsRunner: ShellGeneratorCommandsRunner = DummyGeneratorCommandsRunner()
) : ShellRuntimeContextProvider {
  override fun getContext(typedPrefix: String): ShellRuntimeContext {
    return IJShellRuntimeContext(directory, typedPrefix, shellName, generatorCommandsRunner)
  }
}