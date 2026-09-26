// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface ShellCommandParserOptionsBuilder {
  /**
   * Whether options starting with one hyphen ('-a', '-l', etc.) may have more than one character.
   * If this option is true, then completion engine will parse `-abc` as a single shell option
   * instead of chained options `-a`, `-b` and `-c`.
   *
   * False by default.
   */
  fun flagsArePosixNonCompliant(value: Boolean): ShellCommandParserOptionsBuilder

  /**
   * If true, the options won't be suggested after any argument of the command is typed.
   *
   * False by default.
   */
  fun optionsMustPrecedeArguments(value: Boolean): ShellCommandParserOptionsBuilder

  /**
   * Allows specifying that the option that takes the argument will require having one of these separators
   * between the option name and the argument value.
   */
  fun optionArgSeparators(values: List<String>): ShellCommandParserOptionsBuilder

  fun build(): ShellCommandParserOptions
}

internal class ShellCommandParserOptionsBuilderImpl : ShellCommandParserOptionsBuilder {
  private var flagsArePosixNonCompliant: Boolean = false
  private var optionsMustPrecedeArguments: Boolean = false
  private var optionArgSeparators: List<String> = emptyList()

  override fun flagsArePosixNonCompliant(value: Boolean): ShellCommandParserOptionsBuilder {
    flagsArePosixNonCompliant = value
    return this
  }

  override fun optionsMustPrecedeArguments(value: Boolean): ShellCommandParserOptionsBuilder {
    optionsMustPrecedeArguments = value
    return this
  }

  override fun optionArgSeparators(values: List<String>): ShellCommandParserOptionsBuilder {
    optionArgSeparators = values
    return this
  }

  override fun build(): ShellCommandParserOptions {
    return ShellCommandParserOptionsImpl(
      flagsArePosixNonCompliant = flagsArePosixNonCompliant,
      optionsMustPrecedeArguments = optionsMustPrecedeArguments,
      optionArgSeparators = optionArgSeparators
    )
  }
}