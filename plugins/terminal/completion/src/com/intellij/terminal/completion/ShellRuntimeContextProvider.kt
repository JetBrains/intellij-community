package com.intellij.terminal.completion

import com.intellij.terminal.completion.spec.ShellRuntimeContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ShellRuntimeContextProvider {
  fun getContext(typedPrefix: String): ShellRuntimeContext
}