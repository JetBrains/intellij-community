// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.json

import com.intellij.terminal.block.completion.spec.*
import org.jetbrains.terminal.completion.ShellCommand

internal class ShellJsonBasedCommandSpec(private val data: ShellCommand) : ShellCommandSpec {
  override val names: List<String>
    get() = data.names

  override val displayName: String?
    get() = data.displayName

  override val description: String?
    get() = data.description

  override val insertValue: String?
    get() = data.insertValue

  override val priority: Int
    get() = data.priority

  override val requiresSubcommand: Boolean
    get() = data.requiresSubcommand

  override val parserDirectives: ShellCommandParserDirectives by lazy {
    ShellCommandParserDirectives.create(
      data.parserDirectives.flagsArePosixNoncompliant,
      data.parserDirectives.optionsMustPrecedeArguments,
      data.parserDirectives.optionArgSeparators
    )
  }

  val fullSpecRef: String?
    get() = data.loadSpec

  override val subcommandsGenerator: ShellRuntimeDataGenerator<List<ShellCommandSpec>> = ShellRuntimeDataGenerator {
    data.subcommands.map { ShellJsonBasedCommandSpec(it) }
  }

  override val optionsGenerator: ShellRuntimeDataGenerator<List<ShellOptionSpec>> = ShellRuntimeDataGenerator {
    data.options.map { ShellJsonBasedOptionSpec(it) }
  }

  override val argumentsGenerator: ShellRuntimeDataGenerator<List<ShellArgumentSpec>> = ShellRuntimeDataGenerator {
    data.args.map { ShellJsonBasedArgumentSpec(it) }
  }
}