// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.testFramework.completion.impl

import com.intellij.openapi.project.Project
import com.intellij.terminal.completion.ShellCommandSpecCompletion
import com.intellij.terminal.completion.spec.ShellCommandExecutor
import com.intellij.terminal.completion.spec.ShellCommandSpec
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.block.completion.ShellCommandSpecsManagerImpl
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGeneratorProcessExecutor
import org.jetbrains.plugins.terminal.block.completion.spec.ShellFileSystemSupport
import org.jetbrains.plugins.terminal.testFramework.completion.ShellCompletionTestFixture

@Suppress("TestOnlyProblems")
@TestOnly
internal class ShellCompletionTestFixtureImpl(
  private val project: Project?,
  private val curDirectory: String,
  private val envVariables: Map<String, String>,
  private val isReworkedTerminal: Boolean,
  private val commandSpecs: List<ShellCommandSpec>?,
  private val generatorCommandsRunner: ShellCommandExecutor,
  private val generatorProcessExecutor: ShellDataGeneratorProcessExecutor,
  private val fileSystemSupport: ShellFileSystemSupport,
) : ShellCompletionTestFixture {
  override suspend fun getCompletions(commandText: String): List<ShellCompletionSuggestion> {
    val tokens = commandText.split(Regex(" +"))
    assert(tokens.size > 1) { "Command text must contain a command name and at least a space after it: '$commandText'" }

    val commandSpecsManager = if (commandSpecs != null) {
      TestCommandSpecsManager(commandSpecs)
    }
    else ShellCommandSpecsManagerImpl.getInstance()

    val contextProvider = TestRuntimeContextProvider(
      project = project,
      directory = curDirectory,
      envVariables = envVariables,
      isReworkedTerminal = isReworkedTerminal,
      generatorCommandsRunner = generatorCommandsRunner,
      generatorProcessExecutor = generatorProcessExecutor,
      fileSystemSupport = fileSystemSupport,
    )

    val completion = ShellCommandSpecCompletion(
      commandSpecManager = commandSpecsManager,
      generatorsExecutor = TestGeneratorsExecutor(),
      contextProvider = contextProvider,
    )

    val command = tokens.first()
    val arguments = tokens.drop(1)
    return completion.computeCompletionItems(command, arguments) ?: error("Not found command spec for command: $command")
  }
}