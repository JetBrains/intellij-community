// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.testFramework.completion

import com.intellij.terminal.completion.spec.ShellCommandResult
import com.intellij.terminal.completion.spec.ShellCommandSpec
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

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

  fun build(): ShellCompletionTestFixture
}