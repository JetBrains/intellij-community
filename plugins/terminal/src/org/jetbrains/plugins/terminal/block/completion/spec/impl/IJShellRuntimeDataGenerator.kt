// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.terminal.block.completion.spec.ShellRuntimeContext
import com.intellij.terminal.block.completion.spec.ShellRuntimeDataGenerator

internal class IJShellRuntimeDataGenerator<T>(private val func: suspend (ShellRuntimeContext) -> T) : ShellRuntimeDataGenerator<T> {
  override suspend fun generate(context: ShellRuntimeContext): T {
    return func(context)
  }
}