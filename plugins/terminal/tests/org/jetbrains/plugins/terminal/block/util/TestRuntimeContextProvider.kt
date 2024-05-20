// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.util

import com.intellij.terminal.completion.ShellRuntimeContextProvider
import com.intellij.terminal.completion.spec.ShellName
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellRuntimeContextImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellGeneratorCommandsRunner

internal class TestRuntimeContextProvider(
  private val directory: String = "",
  private val shellName: ShellName = ShellName("dummy"),
  private val generatorCommandsRunner: ShellGeneratorCommandsRunner = DummyGeneratorCommandsRunner()
) : ShellRuntimeContextProvider {
  override fun getContext(typedPrefix: String): ShellRuntimeContext {
    return ShellRuntimeContextImpl(directory, typedPrefix, shellName, generatorCommandsRunner)
  }
}
