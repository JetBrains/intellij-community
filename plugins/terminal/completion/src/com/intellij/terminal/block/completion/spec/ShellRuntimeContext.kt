package com.intellij.terminal.block.completion.spec

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ShellRuntimeContext {
  val currentDirectory: String
  val commandText: String
  val typedPrefix: String
}