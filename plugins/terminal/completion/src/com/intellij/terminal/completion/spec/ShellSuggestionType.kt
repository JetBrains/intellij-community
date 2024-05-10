package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
enum class ShellSuggestionType {
  COMMAND,
  OPTION,
  ARGUMENT,
  FILE,
  FOLDER
}