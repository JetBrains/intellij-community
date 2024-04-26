// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.openapi.util.Key
import com.intellij.terminal.block.completion.ShellDataGeneratorsExecutor
import com.intellij.terminal.block.completion.spec.ShellRuntimeContext
import com.intellij.terminal.block.completion.spec.ShellRuntimeDataGenerator

internal class IJShellGeneratorsExecutor : ShellDataGeneratorsExecutor {
  // TODO: add caching of executed generators
  override suspend fun <T> execute(context: ShellRuntimeContext, generator: ShellRuntimeDataGenerator<T>): T {
    return generator.generate(context)
  }

  companion object {
    val KEY: Key<IJShellGeneratorsExecutor> = Key.create("IJShellGeneratorsExecutor")
  }
}