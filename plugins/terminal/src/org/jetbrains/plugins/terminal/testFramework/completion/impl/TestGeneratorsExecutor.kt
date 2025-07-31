// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.testFramework.completion.impl

import com.intellij.terminal.completion.ShellDataGeneratorsExecutor
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TestGeneratorsExecutor(
  private val mockGeneratorResult: suspend (context: ShellRuntimeContext, generator: ShellRuntimeDataGenerator<*>) -> Any = { context, generator -> generator.generate(context)!! },
) : ShellDataGeneratorsExecutor {
  override suspend fun <T : Any> execute(context: ShellRuntimeContext, generator: ShellRuntimeDataGenerator<T>): T {
    val result = mockGeneratorResult(context, generator)
    @Suppress("UNCHECKED_CAST") // Client should be responsible to match the result type with the generator return type.
    return result as? T ?: error("Mocked result type is not the same as the generator result type. Generator: $generator, result: $result")
  }
}