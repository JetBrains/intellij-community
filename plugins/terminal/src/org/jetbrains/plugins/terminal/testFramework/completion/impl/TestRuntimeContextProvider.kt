// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.testFramework.completion.impl

import com.intellij.openapi.project.Project
import com.intellij.terminal.completion.ShellRuntimeContextProvider
import com.intellij.terminal.completion.spec.ShellCommandExecutor
import com.intellij.terminal.completion.spec.ShellName
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.completion.spec.PROJECT_KEY
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellRuntimeContextImpl

@ApiStatus.Internal
class TestRuntimeContextProvider(
  private val project: Project? = null,
  private val directory: String = "",
  private val shellName: ShellName = ShellName("dummy"),
  private val generatorCommandsRunner: ShellCommandExecutor = TestGeneratorCommandsRunner.DUMMY,
) : ShellRuntimeContextProvider {
  override fun getContext(typedPrefix: String): ShellRuntimeContext {
    return ShellRuntimeContextImpl(directory, typedPrefix, shellName, generatorCommandsRunner).also {
      it.putUserData(PROJECT_KEY, project)
    }
  }
}