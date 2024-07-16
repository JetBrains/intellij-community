// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class ShellCommandParserOptions private constructor(
  val flagsArePosixNonCompliant: Boolean,
  val optionsMustPrecedeArguments: Boolean,
  val optionArgSeparators: List<String>
) {
  override fun toString(): String {
    return "ShellCommandParserDirectives(flagsArePosixNonCompliant=$flagsArePosixNonCompliant, optionsMustPrecedeArguments=$optionsMustPrecedeArguments, optionArgSeparators=$optionArgSeparators)"
  }

  companion object {
    val DEFAULT = create()

    /**
     * @param flagsArePosixNonCompliant whether options starting with one hyphen ('-a', '-l', etc.) may have more than one character.
     * If this option is true, then completion engine will parse `-abc` as a single shell option instead of chained options `-a`, `-b` and `-c`.
     * False by default.
     * @param optionsMustPrecedeArguments if true, the options won't be suggested after any argument of the command is typed. False by default.
     * @param optionArgSeparators allows specifying that the option that takes the argument will require having one of these separators
     * between the option name and the argument value.
     */
    fun create(
      flagsArePosixNonCompliant: Boolean = false,
      optionsMustPrecedeArguments: Boolean = false,
      optionArgSeparators: List<String> = emptyList()
    ): ShellCommandParserOptions {
      return ShellCommandParserOptions(flagsArePosixNonCompliant, optionsMustPrecedeArguments, optionArgSeparators)
    }
  }
}