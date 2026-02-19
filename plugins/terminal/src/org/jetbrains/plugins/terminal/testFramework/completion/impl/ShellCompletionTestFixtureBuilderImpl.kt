// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.testFramework.completion.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.terminal.completion.spec.ShellCommandExecutor
import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.terminal.completion.spec.ShellCommandSpec
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGeneratorProcessExecutor
import org.jetbrains.plugins.terminal.block.completion.spec.ShellFileSystemSupport
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellDataGeneratorProcessExecutorImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellFileSystemSupportImpl
import org.jetbrains.plugins.terminal.testFramework.completion.ShellCompletionTestFixture
import org.jetbrains.plugins.terminal.testFramework.completion.ShellCompletionTestFixtureBuilder

@Suppress("TestOnlyProblems")
@TestOnly
internal class ShellCompletionTestFixtureBuilderImpl(private val project: Project) : ShellCompletionTestFixtureBuilder {
  private var curDirectory: String = project.guessProjectDir()!!.path
  private var envVariables: Map<String, String> = emptyMap()
  private var isReworkedTerminal: Boolean = true
  private var commandSpecs: List<ShellCommandSpec>? = null
  private var generatorCommandsRunner: ShellCommandExecutor = DummyShellCommandExecutor
  private var generatorProcessExecutor: ShellDataGeneratorProcessExecutor? = null
  private var fileSystemSupport: ShellFileSystemSupport? = null

  override fun setCurrentDirectory(directory: String): ShellCompletionTestFixtureBuilder {
    curDirectory = directory
    return this
  }

  override fun setEnvVariables(envVars: Map<String, String>): ShellCompletionTestFixtureBuilder {
    envVariables = envVars
    return this
  }

  override fun setIsReworkedTerminal(isReworkedTerminal: Boolean): ShellCompletionTestFixtureBuilder {
    this.isReworkedTerminal = isReworkedTerminal
    return this
  }

  override fun mockCommandSpecs(vararg specs: ShellCommandSpec): ShellCompletionTestFixtureBuilder {
    commandSpecs = specs.toList()
    return this
  }

  override fun mockShellCommandResults(mock: suspend (command: String) -> ShellCommandResult): ShellCompletionTestFixtureBuilder {
    generatorCommandsRunner = object : ShellCommandExecutor {
      override suspend fun runShellCommand(directory: String, command: String): ShellCommandResult = mock(command)
    }
    return this
  }

  override fun mockProcessesExecutor(executor: ShellDataGeneratorProcessExecutor): ShellCompletionTestFixtureBuilder {
    generatorProcessExecutor = executor
    return this
  }

  override fun mockFileSystemSupport(support: ShellFileSystemSupport): ShellCompletionTestFixtureBuilder {
    fileSystemSupport = support
    return this
  }

  override fun build(): ShellCompletionTestFixture {
    val eelDescriptor = project.getEelDescriptor()
    val processExecutor = generatorProcessExecutor
                          ?: ShellDataGeneratorProcessExecutorImpl(eelDescriptor, envVariables)
    val fileSystemSupport = fileSystemSupport ?: ShellFileSystemSupportImpl(eelDescriptor)
    return ShellCompletionTestFixtureImpl(
      project = project,
      curDirectory = curDirectory,
      envVariables = envVariables,
      isReworkedTerminal = isReworkedTerminal,
      commandSpecs = commandSpecs,
      generatorCommandsRunner = generatorCommandsRunner,
      generatorProcessExecutor = processExecutor,
      fileSystemSupport = fileSystemSupport,
    )
  }
}