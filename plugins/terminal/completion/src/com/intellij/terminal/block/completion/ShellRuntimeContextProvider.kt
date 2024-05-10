package com.intellij.terminal.block.completion

import com.intellij.terminal.block.completion.spec.ShellRuntimeContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ShellRuntimeContextProvider {
  fun getContext(typedPrefix: String): ShellRuntimeContext
}