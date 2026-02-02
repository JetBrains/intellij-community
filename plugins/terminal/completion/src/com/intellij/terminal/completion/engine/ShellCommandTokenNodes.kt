// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion.engine

import com.intellij.terminal.completion.spec.ShellAliasSuggestion
import com.intellij.terminal.completion.spec.ShellArgumentSpec
import com.intellij.terminal.completion.spec.ShellCommandParserOptions
import com.intellij.terminal.completion.spec.ShellCommandSpec
import com.intellij.terminal.completion.spec.ShellOptionSpec

internal abstract class ShellCommandTreeNode<T>(val text: String, open val spec: T?, val parent: ShellCommandTreeNode<*>?) {
  val children: MutableList<ShellCommandTreeNode<*>> = mutableListOf()

  override fun toString(): String {
    return "${javaClass.simpleName} { text: $text, children: $children }"
  }
}

internal class ShellCommandNode(
  text: String,
  override val spec: ShellCommandSpec,
  parent: ShellCommandTreeNode<*>?
) : ShellCommandTreeNode<ShellCommandSpec>(text, spec, parent) {
  fun getMergedParserOptions(): ShellCommandParserOptions {
    val directives = mutableListOf<ShellCommandParserOptions>()
    directives.add(spec.parserOptions)
    var cur = parent
    while (cur is ShellCommandNode) {
      directives.add(cur.spec.parserOptions)
      cur = cur.parent
    }
    return directives.asReversed().reduce { base, child -> mergeParserOptions(base, child) }
  }

  // child values takes precedence over base only if they are not default
  private fun mergeParserOptions(base: ShellCommandParserOptions, child: ShellCommandParserOptions): ShellCommandParserOptions {
    val flagsArePosixNonCompliant = if (child.flagsArePosixNonCompliant) true else base.flagsArePosixNonCompliant
    val optionsMustPrecedeArguments = if (child.optionsMustPrecedeArguments) true else base.optionsMustPrecedeArguments
    val optionArgSeparators = (base.optionArgSeparators + child.optionArgSeparators).distinct()
    return ShellCommandParserOptions.builder()
      .flagsArePosixNonCompliant(flagsArePosixNonCompliant)
      .optionsMustPrecedeArguments(optionsMustPrecedeArguments)
      .optionArgSeparators(optionArgSeparators)
      .build()
  }
}

internal class ShellOptionNode(
  text: String,
  override val spec: ShellOptionSpec,
  parent: ShellCommandTreeNode<*>?
) : ShellCommandTreeNode<ShellOptionSpec>(text, spec, parent)

internal class ShellArgumentNode(
  text: String,
  override val spec: ShellArgumentSpec,
  parent: ShellCommandTreeNode<*>?
) : ShellCommandTreeNode<ShellArgumentSpec>(text, spec, parent)

internal class ShellAliasNode(
  text: String,
  override val spec: ShellAliasSuggestion,
  parent: ShellCommandTreeNode<*>?
) : ShellCommandTreeNode<ShellAliasSuggestion>(text, spec, parent)

internal class ShellUnknownNode(text: String, parent: ShellCommandTreeNode<*>?) : ShellCommandTreeNode<Any>(text, null, parent)