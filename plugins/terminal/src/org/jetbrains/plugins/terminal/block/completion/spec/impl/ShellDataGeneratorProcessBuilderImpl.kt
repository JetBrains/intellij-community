// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.terminal.completion.spec.ShellDataGeneratorProcessBuilder
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGeneratorProcessExecutor
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGeneratorProcessOptionsImpl

internal class ShellDataGeneratorProcessBuilderImpl(
  private val executable: String,
  defaultWorkingDirectory: String,
  private val executor: ShellDataGeneratorProcessExecutor,
) : ShellDataGeneratorProcessBuilder {
  private var args: List<String> = emptyList()
  private var workingDirectory: String = defaultWorkingDirectory
  private var env: Map<String, String> = emptyMap()

  override fun args(args: List<String>): ShellDataGeneratorProcessBuilder {
    this.args = args
    return this
  }

  override fun workingDirectory(workingDirectory: String): ShellDataGeneratorProcessBuilder {
    this.workingDirectory = workingDirectory
    return this
  }

  override fun env(env: Map<String, String>): ShellDataGeneratorProcessBuilder {
    this.env = env
    return this
  }

  override suspend fun execute(): ShellCommandResult {
    val options = ShellDataGeneratorProcessOptionsImpl(
      executable = executable,
      args = args,
      workingDirectory = workingDirectory,
      env = env
    )
    return executor.executeProcess(options)
  }
}