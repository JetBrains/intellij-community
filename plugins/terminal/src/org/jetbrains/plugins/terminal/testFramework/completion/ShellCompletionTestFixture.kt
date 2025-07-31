// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.testFramework.completion

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.terminal.completion.ShellCommandSpecCompletion
import com.intellij.terminal.completion.ShellDataGeneratorsExecutor
import com.intellij.terminal.completion.spec.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.block.completion.ShellCommandSpecsManagerImpl
import org.jetbrains.plugins.terminal.testFramework.completion.impl.TestCommandSpecsManager
import org.jetbrains.plugins.terminal.testFramework.completion.impl.TestGeneratorCommandsRunner
import org.jetbrains.plugins.terminal.testFramework.completion.impl.TestGeneratorsExecutor
import org.jetbrains.plugins.terminal.testFramework.completion.impl.TestRuntimeContextProvider

/**
 * Fixture for testing shell command specification-based completion in a New Terminal.
 *
 * Can be used to test new [ShellCommandSpec]'s and ensure that completion items are relevant in the test cases.
 * Allows mocking the shell current directory, shell name, and [ShellRuntimeDataGenerator] results.
 * This fixture is not running the terminal session, mocking is used to provide the actual results for shell commands.
 */
@ApiStatus.Experimental
@TestOnly
class ShellCompletionTestFixture private constructor(
  private val project: Project?,
  private val curDirectory: String,
  private val shellName: ShellName,
  private val commandSpecs: List<ShellCommandSpec>?,
  private val generatorCommandsRunner: ShellCommandExecutor,
  private val generatorsExecutor: ShellDataGeneratorsExecutor,
) {
  /**
   * Returns the completion suggestions for provided [commandText].
   * It is intended that caret is positioned at the end of the [commandText], so the suggestions are returned for the last word.
   * Note that the results are not filtered by the prefix of the last word in the [commandText].
   * The command text should consist of the command name and at least a space after it (empty prefix).
   * For example,
   * 1. `git ` (with a trailing space) -> should return all subcommands and options of `git`.
   * 2. `git --he` -> the same as the previous. No matching or filtering of suggestions is performed.
   * 3. `git branch -b ` (with a trailing space) -> should return git branch suggestions.
   */
  suspend fun getCompletions(commandText: String): List<ShellCompletionSuggestion> {
    val tokens = commandText.split(Regex(" +"))
    assert(tokens.size > 1) { "Command text must contain a command name and at least a space after it: '$commandText'" }

    val commandSpecsManager = if (commandSpecs != null) {
      TestCommandSpecsManager(commandSpecs)
    }
    else ShellCommandSpecsManagerImpl.Companion.getInstance()

    val completion = ShellCommandSpecCompletion(
      commandSpecsManager,
      generatorsExecutor,
      TestRuntimeContextProvider(project, curDirectory, shellName, generatorCommandsRunner)
    )

    val command = tokens.first()
    val arguments = tokens.drop(1)
    return completion.computeCompletionItems(command, arguments) ?: error("Not found command spec for command: $command")
  }

  @TestOnly
  class Builder internal constructor(private val project: Project?) {
    private var curDirectory: String = project?.guessProjectDir()?.path ?: ""
    private var shellName: ShellName = ShellName("dummy")
    private var commandSpecs: List<ShellCommandSpec>? = null
    private var generatorCommandsRunner: ShellCommandExecutor = TestGeneratorCommandsRunner.Companion.DUMMY
    private var generatorsExecutor: ShellDataGeneratorsExecutor = TestGeneratorsExecutor()

    /**
     * This current directory will be used in the [ShellRuntimeContext] provided to [ShellRuntimeDataGenerator]'s.
     * By default, it is a path of the [project] dir if it is provided, or an empty string if it is not.
     */
    fun setCurrentDirectory(directory: String): Builder {
      curDirectory = directory
      return this
    }

    /**
     * This shell name will be used in the [ShellRuntimeContext] provided to [ShellRuntimeDataGenerator]'s.
     * By default, the shell name is 'dummy'.
     */
    fun setShellName(name: ShellName): Builder {
      shellName = name
      return this
    }

    /**
     * Allows mocking the available command specs for which completion can be provided.
     * By default, we use all command specs available in production, but it requires starting the IDE application.
     * If your test is not starting the IDE, available command specs must be provided using this method.
     */
    fun mockCommandSpecs(vararg specs: ShellCommandSpec): Builder {
      commandSpecs = specs.toList()
      return this
    }

    /**
     * Allows mocking the results of the [ShellRuntimeContext.runShellCommand] calls in the [ShellRuntimeDataGenerator]'s.
     * By default, the empty [ShellCommandResult] is returned with exit code 0,
     * because this test fixture is not running the real shell session.
     */
    fun mockShellCommandResults(mock: suspend (command: String) -> ShellCommandResult): Builder {
      generatorCommandsRunner = ShellCommandExecutor(mock)
      return this
    }

    /**
     * Allows mocking the results of the whole [ShellRuntimeDataGenerator]'s.
     * You can use `toString` method of the generator to get its debug name and match with the generator you want to mock.
     * You can specify the debug name at the moment of the generator creation
     * using [helper function][org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator].
     * Debug names are also specified for the generators created by the platform.
     * You can find the exact names by logging the `toString` of the generators ran during your test case.
     *
     * Note that you should ensure that the type of the returned result matches the expected generator result type.
     *
     * By default, the generator is just executed for the provided [ShellRuntimeContext].
     */
    fun mockDataGeneratorResults(
      mock: suspend (context: ShellRuntimeContext, generator: ShellRuntimeDataGenerator<*>) -> Any,
    ): Builder {
      generatorsExecutor = TestGeneratorsExecutor(mock)
      return this
    }

    fun build(): ShellCompletionTestFixture {
      return ShellCompletionTestFixture(project, curDirectory, shellName, commandSpecs, generatorCommandsRunner, generatorsExecutor)
    }
  }

  @TestOnly
  companion object {
    fun builder(project: Project?): Builder = Builder(project)
  }
}