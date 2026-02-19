// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.testFramework.completion

import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.terminal.completion.spec.ShellCommandSpec
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGeneratorProcessExecutor
import org.jetbrains.plugins.terminal.block.completion.spec.ShellFileSystemSupport

@ApiStatus.Experimental
@ApiStatus.NonExtendable
@TestOnly
interface ShellCompletionTestFixtureBuilder {
  /**
   * This current directory will be used in the [ShellRuntimeContext] provided to [ShellRuntimeDataGenerator]'s.
   * By default, it is a path of the [project] dir.
   */
  fun setCurrentDirectory(directory: String): ShellCompletionTestFixtureBuilder

  /**
   * These variables will be returned from [ShellRuntimeContext.envVariables].
   * By default, the env variables map is empty.
   */
  fun setEnvVariables(envVars: Map<String, String>): ShellCompletionTestFixtureBuilder

  /**
   * Allows mocking the result of [org.jetbrains.plugins.terminal.block.completion.spec.isReworkedTerminal] check.
   * By default, it is **true**.
   *
   * Set this flag to false only if you need to test the logic that relates to the Experimental 2024 Terminal.
   */
  fun setIsReworkedTerminal(isReworkedTerminal: Boolean): ShellCompletionTestFixtureBuilder

  /**
   * Allows replacing the available command specs for which completion can be provided.
   * By default, we use all command specs available in production (depends on the class path used to start the test).
   */
  fun mockCommandSpecs(vararg specs: ShellCommandSpec): ShellCompletionTestFixtureBuilder

  /**
   * Allows mocking the results of the [ShellRuntimeContext.runShellCommand] calls in the [ShellRuntimeDataGenerator]'s.
   * By default, the empty [ShellCommandResult] is returned with exit code 0,
   * because this test fixture is not running the real shell session.
   */
  fun mockShellCommandResults(mock: suspend (command: String) -> ShellCommandResult): ShellCompletionTestFixtureBuilder

  /**
   * Allows mocking the results of the [ShellRuntimeContext.createProcessBuilder] calls in the [ShellRuntimeDataGenerator]'s.
   * By default, the same executor is used as in production,
   * i.e., it launches a real process in the environment where a project is opened.
   *
   * You can override [ShellDataGeneratorProcessExecutor] and provide it here, for example, to not launch any real processes,
   * and return mocked process execution results.
   * This way you can test the logic of the command spec without configuring a real environment.
   */
  fun mockProcessesExecutor(executor: ShellDataGeneratorProcessExecutor): ShellCompletionTestFixtureBuilder

  /**
   * Allows mocking the results of the [ShellRuntimeContext.listDirectoryFiles] calls in the [ShellRuntimeDataGenerator]'s.
   * By default, the same file system support is used as in production,
   * i.e., it looks for files in the environment where a project is opened.
   *
   * You can override [ShellFileSystemSupport] and provide it here, for example, to return mocked files.
   * This way you can test the logic of the command spec without configuring a real environment.
   */
  fun mockFileSystemSupport(support: ShellFileSystemSupport): ShellCompletionTestFixtureBuilder

  fun build(): ShellCompletionTestFixture
}