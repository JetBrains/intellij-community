package com.intellij.terminal.block.completion.spec

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
enum class ShellSuggestionType {
  COMMAND,
  OPTION,
  ARGUMENT,
  FILE,
  FOLDER
}