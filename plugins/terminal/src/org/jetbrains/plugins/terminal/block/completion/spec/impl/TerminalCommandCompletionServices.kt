// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.openapi.util.Key
import com.intellij.terminal.completion.ShellCommandSpecsManager
import com.intellij.terminal.completion.ShellDataGeneratorsExecutor
import com.intellij.terminal.completion.ShellRuntimeContextProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.completion.spec.impl.TerminalCommandCompletionServices.Companion.KEY

/**
 * Just a wrapper for all the services needed for command completion
 * to make them accessible under the single [KEY].
 */
@ApiStatus.Internal
class TerminalCommandCompletionServices(
  val commandSpecsManager: ShellCommandSpecsManager,
  val runtimeContextProvider: ShellRuntimeContextProvider,
  val dataGeneratorsExecutor: ShellDataGeneratorsExecutor,
) {
  companion object {
    val KEY: Key<TerminalCommandCompletionServices> = Key.create("TerminalCommandCompletionServices")
  }
}