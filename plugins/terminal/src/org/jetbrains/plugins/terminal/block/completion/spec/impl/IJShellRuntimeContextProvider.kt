// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.openapi.util.Key
import com.intellij.terminal.block.completion.ShellRuntimeContextProvider
import com.intellij.terminal.block.completion.spec.ShellRuntimeContext
import com.intellij.util.PathUtil

internal class IJShellRuntimeContextProvider : ShellRuntimeContextProvider {
  @Volatile
  private var curDirectory: String = ""

  override fun getContext(commandText: String, typedPrefix: String): ShellRuntimeContext {
    return IJShellRuntimeContext(curDirectory, commandText, typedPrefix)
  }

  fun updateCurrentDirectory(directory: String) {
    curDirectory = PathUtil.toSystemIndependentName(directory)
  }

  companion object {
    val KEY: Key<IJShellRuntimeContextProvider> = Key.create("IJShellRuntimeContextProvider")
  }
}