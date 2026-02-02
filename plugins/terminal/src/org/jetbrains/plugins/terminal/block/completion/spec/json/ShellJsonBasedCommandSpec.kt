// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.json

import com.intellij.terminal.completion.spec.ShellArgumentSpec
import com.intellij.terminal.completion.spec.ShellCommandParserOptions
import com.intellij.terminal.completion.spec.ShellCommandSpec
import com.intellij.terminal.completion.spec.ShellOptionSpec
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.createCacheKey
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.terminal.completion.ShellCommand
import javax.swing.Icon

/**
 * @param [parentNames] used to build cache key/debug name of the subcommand/option/argument generators
 */
internal class ShellJsonBasedCommandSpec(
  override val name: String,
  private val data: ShellCommand,
  parentNames: List<String> = emptyList()
) : ShellCommandSpec {
  override val displayName: String?
    get() = data.displayName

  override val description: String?
    get() = data.description

  override val insertValue: String?
    get() = data.insertValue

  override val priority: Int
    get() = data.priority

  // the icon of command will be specified in the completion logic
  override val icon: Icon? = null

  override val prefixReplacementIndex: Int = 0

  override val isHidden: Boolean = false

  override val shouldEscape: Boolean = true

  override val requiresSubcommand: Boolean
    get() = data.requiresSubcommand

  override val parserOptions: ShellCommandParserOptions by lazy {
    ShellCommandParserOptions.builder()
      .flagsArePosixNonCompliant(data.parserDirectives.flagsArePosixNoncompliant)
      .optionsMustPrecedeArguments(data.parserDirectives.optionsMustPrecedeArguments)
      .optionArgSeparators(data.parserDirectives.optionArgSeparators)
      .build()
  }

  val fullSpecRef: String?
    get() = data.loadSpec

  private val parentNamesWithSelf: List<String> = parentNames + name

  private val subcommands: List<ShellCommandSpec> by lazy {
    data.subcommands.flatMap { cmd ->
      cmd.names.map { name -> ShellJsonBasedCommandSpec(name, cmd, parentNamesWithSelf) }
    }
  }
  override val options: List<ShellOptionSpec> by lazy {
    data.options.flatMap { opt ->
      opt.names.map { name -> ShellJsonBasedOptionSpec(name, opt, parentNamesWithSelf) }
    }
  }
  override val arguments: List<ShellArgumentSpec> by lazy {
    data.args.map { ShellJsonBasedArgumentSpec(it, parentNamesWithSelf) }
  }

  override val subcommandsGenerator: ShellRuntimeDataGenerator<List<ShellCommandSpec>> =
    ShellRuntimeDataGenerator(debugName = createCacheKey(parentNamesWithSelf, "subcommands")) { subcommands }

  override val allOptionsGenerator: ShellRuntimeDataGenerator<List<ShellOptionSpec>> =
    ShellRuntimeDataGenerator(debugName = createCacheKey(parentNamesWithSelf, "options")) { options }

  override fun toString(): String {
    return "ShellJsonBasedCommandSpec(name=$name, parentNamesWithSelf=$parentNamesWithSelf, data=$data)"
  }
}
