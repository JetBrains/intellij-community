// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.testFramework.completion

import com.intellij.terminal.completion.spec.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Experimental
@ApiStatus.NonExtendable
@TestOnly
interface ShellCompletionTestFixtureBuilder {
  /**
   * This current directory will be used in the [ShellRuntimeContext] provided to [ShellRuntimeDataGenerator]'s.
   * By default, it is a path of the [project] dir if it is provided, or an empty string if it is not.
   */
  fun setCurrentDirectory(directory: String): ShellCompletionTestFixtureBuilder

  /**
   * This shell name will be used in the [ShellRuntimeContext] provided to [ShellRuntimeDataGenerator]'s.
   * By default, the shell name is 'dummy'.
   */
  fun setShellName(name: ShellName): ShellCompletionTestFixtureBuilder

  /**
   * Allows mocking the available command specs for which completion can be provided.
   * By default, we use all command specs available in production, but it requires starting the IDE application.
   * If your test is not starting the IDE, available command specs must be provided using this method.
   */
  fun mockCommandSpecs(vararg specs: ShellCommandSpec): ShellCompletionTestFixtureBuilder

  /**
   * Allows mocking the results of the [ShellRuntimeContext.runShellCommand] calls in the [ShellRuntimeDataGenerator]'s.
   * By default, the empty [ShellCommandResult] is returned with exit code 0,
   * because this test fixture is not running the real shell session.
   */
  fun mockShellCommandResults(mock: suspend (command: String) -> ShellCommandResult): ShellCompletionTestFixtureBuilder

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
  ): ShellCompletionTestFixtureBuilder

  fun build(): ShellCompletionTestFixture
}