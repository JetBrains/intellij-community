// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.block.completion.engine

import org.jetbrains.terminal.completion.ShellArgument
import org.jetbrains.terminal.completion.ShellCommand
import org.jetbrains.terminal.completion.ShellCommandParserDirectives
import org.jetbrains.terminal.completion.ShellOption

internal abstract class ShellCommandTreeNode<T>(val text: String, open val spec: T?, val parent: ShellCommandTreeNode<*>?) {
  val children: MutableList<ShellCommandTreeNode<*>> = mutableListOf()

  override fun toString(): String {
    return "${javaClass.simpleName} { text: $text, children: $children }"
  }
}

internal class ShellCommandNode(
  text: String,
  override val spec: ShellCommand,
  parent: ShellCommandTreeNode<*>?
) : ShellCommandTreeNode<ShellCommand>(text, spec, parent) {
  fun getMergedParserDirectives(): ShellCommandParserDirectives {
    val directives = mutableListOf<ShellCommandParserDirectives>()
    directives.add(spec.parserDirectives)
    var cur = parent
    while (cur is ShellCommandNode) {
      directives.add(cur.spec.parserDirectives)
      cur = cur.parent
    }
    return directives.asReversed().reduce { base, child -> mergeDirectives(base, child) }
  }

  // child values takes precedence over base only if they are not default
  private fun mergeDirectives(base: ShellCommandParserDirectives, child: ShellCommandParserDirectives): ShellCommandParserDirectives {
    val flagsArePosixNoncompliant = if (child.flagsArePosixNoncompliant) true else base.flagsArePosixNoncompliant
    val optionsMustPrecedeArguments = if (child.optionsMustPrecedeArguments) true else base.optionsMustPrecedeArguments
    val optionArgSeparators = (base.optionArgSeparators + child.optionArgSeparators).toSet().toList()
    return ShellCommandParserDirectives(flagsArePosixNoncompliant, optionsMustPrecedeArguments, optionArgSeparators)
  }
}

internal class ShellOptionNode(
  text: String,
  override val spec: ShellOption,
  parent: ShellCommandTreeNode<*>?
) : ShellCommandTreeNode<ShellOption>(text, spec, parent)

internal class ShellArgumentNode(
  text: String,
  override val spec: ShellArgument,
  parent: ShellCommandTreeNode<*>?
) : ShellCommandTreeNode<ShellArgument>(text, spec, parent)

internal class ShellUnknownNode(text: String, parent: ShellCommandTreeNode<*>?) : ShellCommandTreeNode<Any>(text, null, parent)