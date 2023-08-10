// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion

import org.jetbrains.terminal.completion.ShellArgument
import org.jetbrains.terminal.completion.ShellCommand
import org.jetbrains.terminal.completion.ShellCommandParserDirectives
import org.jetbrains.terminal.completion.ShellOption

internal abstract class CommandPartNode<T>(val text: String, open val spec: T?, val parent: CommandPartNode<*>?) {
  val children: MutableList<CommandPartNode<*>> = mutableListOf()

  override fun toString(): String {
    return "${javaClass.simpleName} { text: $text, children: $children }"
  }
}

internal class SubcommandNode(text: String,
                              override val spec: ShellCommand,
                              parent: CommandPartNode<*>?) : CommandPartNode<ShellCommand>(text, spec, parent) {
  fun getMergedParserDirectives(): ShellCommandParserDirectives {
    val directives = mutableListOf<ShellCommandParserDirectives>()
    directives.add(spec.parserDirectives)
    var cur = parent
    while (cur is SubcommandNode) {
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

internal class OptionNode(text: String,
                          override val spec: ShellOption,
                          parent: CommandPartNode<*>?) : CommandPartNode<ShellOption>(text, spec, parent)

internal class ArgumentNode(text: String,
                            override val spec: ShellArgument,
                            parent: CommandPartNode<*>?) : CommandPartNode<ShellArgument>(text, spec, parent)

internal class UnknownNode(text: String, parent: CommandPartNode<*>?) : CommandPartNode<Any>(text, null, parent)