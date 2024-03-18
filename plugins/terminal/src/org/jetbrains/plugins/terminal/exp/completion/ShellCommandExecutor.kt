// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.openapi.util.Key

interface ShellCommandExecutor {
  suspend fun <T> executeCommand(command: DataProviderCommand<T>): T

  companion object {
    val KEY: Key<ShellCommandExecutor> = Key.create("ShellCommandExecutor")
  }
}