// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.util

import org.jetbrains.plugins.terminal.exp.completion.ShellRuntimeDataProvider

class FakeShellRuntimeDataProvider : ShellRuntimeDataProvider {
  override fun getFilesFromDirectory(path: String): List<String> {
    return emptyList()
  }
}