// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion.util

import com.intellij.terminal.completion.ShellEnvironment
import com.intellij.terminal.completion.ShellRuntimeDataProvider

class FakeShellRuntimeDataProvider(
  private val filesToReturn: List<String> = emptyList(),
  private val shellEnvironment: ShellEnvironment? = null,
) : ShellRuntimeDataProvider {
  override suspend fun getFilesFromDirectory(path: String): List<String> {
    return filesToReturn
  }

  override suspend fun getShellEnvironment(): ShellEnvironment? {
    return shellEnvironment
  }
}