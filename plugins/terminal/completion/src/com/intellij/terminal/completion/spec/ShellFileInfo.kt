package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ShellFileInfo {
  val name: String
  val type: Type

  @ApiStatus.Experimental
  enum class Type {
    FILE, DIRECTORY, OTHER
  }
}