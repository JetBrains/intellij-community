// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.terminal.completion.ShellDataGeneratorsExecutor
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator

class ShellDataGeneratorsExecutorReworkedImpl : ShellDataGeneratorsExecutor {
  override suspend fun <T : Any> execute(context: ShellRuntimeContext, generator: ShellRuntimeDataGenerator<T>): T {
    return generator.generate(context)
  }
}