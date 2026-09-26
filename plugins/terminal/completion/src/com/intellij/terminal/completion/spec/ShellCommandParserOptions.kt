// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface ShellCommandParserOptions {
  /**
   * @see [ShellCommandParserOptionsBuilder.flagsArePosixNonCompliant]
   */
  val flagsArePosixNonCompliant: Boolean

  /**
   * @see [ShellCommandParserOptionsBuilder.optionsMustPrecedeArguments]
   */
  val optionsMustPrecedeArguments: Boolean

  /**
   * @see [ShellCommandParserOptionsBuilder.optionArgSeparators]
   */
  val optionArgSeparators: List<String>

  companion object {
    val DEFAULT: ShellCommandParserOptions = builder().build()

    fun builder(): ShellCommandParserOptionsBuilder = ShellCommandParserOptionsBuilderImpl()
  }
}

internal data class ShellCommandParserOptionsImpl(
  override val flagsArePosixNonCompliant: Boolean,
  override val optionsMustPrecedeArguments: Boolean,
  override val optionArgSeparators: List<String>,
) : ShellCommandParserOptions