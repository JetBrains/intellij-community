package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface ShellFileInfo {
  val name: String
  val type: Type

  @ApiStatus.Experimental
  enum class Type {
    FILE, DIRECTORY, OTHER
  }

  companion object {
    fun create(name: String, type: Type): ShellFileInfo = ShellFileInfoImpl(name, type)
  }
}

private data class ShellFileInfoImpl(
  override val name: String,
  override val type: ShellFileInfo.Type,
) : ShellFileInfo