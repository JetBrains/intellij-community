// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.block.completion.spec

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class ShellCommandParserDirectives private constructor(
  val flagsArePosixNonCompliant: Boolean,
  val optionsMustPrecedeArguments: Boolean,
  val optionArgSeparators: List<String>
) {
  override fun toString(): String {
    return "ShellCommandParserDirectives(flagsArePosixNonCompliant=$flagsArePosixNonCompliant, optionsMustPrecedeArguments=$optionsMustPrecedeArguments, optionArgSeparators=$optionArgSeparators)"
  }

  companion object {
    val DEFAULT = create()

    fun create(
      flagsArePosixNonCompliant: Boolean = false,
      optionsMustPrecedeArguments: Boolean = false,
      optionArgSeparators: List<String> = emptyList()
    ): ShellCommandParserDirectives {
      return ShellCommandParserDirectives(flagsArePosixNonCompliant, optionsMustPrecedeArguments, optionArgSeparators)
    }
  }
}