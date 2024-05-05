// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.json

import com.intellij.terminal.block.completion.spec.*
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.createCacheKey
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.terminal.completion.ShellCommand

/**
 * @param [parentNames] used to build cache key/debug name of the subcommand/option/argument generators
 */
internal class ShellJsonBasedCommandSpec(
  private val data: ShellCommand,
  parentNames: List<String> = emptyList()
) : ShellCommandSpec {
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

  private val parentNamesWithSelf: List<String> = parentNames + data.names.first()

  override val subcommandsGenerator: ShellRuntimeDataGenerator<List<ShellCommandSpec>> =
    ShellRuntimeDataGenerator(createCacheKey(parentNamesWithSelf, "subcommands")) {
      data.subcommands.map { ShellJsonBasedCommandSpec(it, parentNamesWithSelf) }
    }

  override val optionsGenerator: ShellRuntimeDataGenerator<List<ShellOptionSpec>> =
    ShellRuntimeDataGenerator(createCacheKey(parentNamesWithSelf, "options")) {
      data.options.map { ShellJsonBasedOptionSpec(it, parentNamesWithSelf) }
    }

  override val argumentsGenerator: ShellRuntimeDataGenerator<List<ShellArgumentSpec>> =
    ShellRuntimeDataGenerator(createCacheKey(parentNamesWithSelf, "arguments")) {
      data.args.map { ShellJsonBasedArgumentSpec(it, parentNamesWithSelf) }
    }
}