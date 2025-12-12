// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.testFramework.completion.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.terminal.completion.ShellDataGeneratorsExecutor
import com.intellij.terminal.completion.spec.*
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.testFramework.completion.ShellCompletionTestFixture
import org.jetbrains.plugins.terminal.testFramework.completion.ShellCompletionTestFixtureBuilder

@Suppress("TestOnlyProblems")
@TestOnly
internal class ShellCompletionTestFixtureBuilderImpl(private val project: Project?) : ShellCompletionTestFixtureBuilder {
  private var curDirectory: String = project?.guessProjectDir()?.path ?: ""
  private var shellName: ShellName = ShellName("dummy")
  private var commandSpecs: List<ShellCommandSpec>? = null
  private var generatorCommandsRunner: ShellCommandExecutor = DummyShellCommandExecutor
  private var generatorsExecutor: ShellDataGeneratorsExecutor = TestGeneratorsExecutor()

  override fun setCurrentDirectory(directory: String): ShellCompletionTestFixtureBuilder {
    curDirectory = directory
    return this
  }

  override fun setShellName(name: ShellName): ShellCompletionTestFixtureBuilder {
    shellName = name
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

  override fun mockDataGeneratorResults(mock: suspend (context: ShellRuntimeContext, generator: ShellRuntimeDataGenerator<*>) -> Any): ShellCompletionTestFixtureBuilder {
    generatorsExecutor = TestGeneratorsExecutor(mock)
    return this
  }

  override fun build(): ShellCompletionTestFixture {
    return ShellCompletionTestFixtureImpl(project, curDirectory, shellName, commandSpecs, generatorCommandsRunner, generatorsExecutor)
  }
}