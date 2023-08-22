// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion.util

import org.jetbrains.terminal.completion.*

@DslMarker
annotation class CommandSpecDsl

@CommandSpecDsl
internal fun commandSpec(vararg names: String, content: SubcommandContext.() -> Unit): ShellCommand {
  val context = SubcommandContextImpl(names.asList())
  content(context)
  return context.build()
}

@CommandSpecDsl
internal interface BaseCommandPartContext {
  var displayName: String?
  var insertValue: String?
  var description: String?
  var priority: Int
  var hidden: Boolean
}

@CommandSpecDsl
internal interface SubcommandContext : BaseCommandPartContext {
  var requiresSubcommand: Boolean
  var parserDirectives: ShellCommandParserDirectives

  fun subcommand(vararg names: String, content: (SubcommandContext.() -> Unit)? = null)
  fun option(vararg names: String, content: (OptionContext.() -> Unit)? = null)
  fun argument(displayName: String, isOptional: Boolean = false, content: (ArgumentContext.() -> Unit)? = null)
  fun additionalSuggestion(suggestion: ShellSuggestion)
}

@CommandSpecDsl
internal interface OptionContext {
  var isPersistent: Boolean
  var isRequired: Boolean
  var separator: String?
  var repeatTimes: Int

  fun argument(name: String, isOptional: Boolean = false, content: (ArgumentContext.() -> Unit)? = null)
  fun exclusiveOn(vararg options: String)
  fun dependsOn(vararg options: String)
}

@CommandSpecDsl
internal interface ArgumentContext {
  var description: String?
  var isVariadic: Boolean
  var optionsCanBreakVariadicArg: Boolean
  var isCommand: Boolean
  var default: String?

  fun suggestion(value: ShellSuggestion)
  fun suggestions(vararg values: String)
  fun templates(vararg values: String)
  fun generator(value: ShellSuggestionsGenerator)
}

private abstract class BaseCommandPartContextImpl : BaseCommandPartContext {
  override var displayName: String? = null
  override var insertValue: String? = null
  override var description: String? = null
  override var priority: Int = 50
  override var hidden: Boolean = false
}

private class SubcommandContextImpl(private val names: List<String>) : SubcommandContext, BaseCommandPartContextImpl() {
  override var requiresSubcommand: Boolean = false
  override var parserDirectives = ShellCommandParserDirectives()

  private val subcommands: MutableList<ShellCommand> = mutableListOf()
  private val options: MutableList<ShellOption> = mutableListOf()
  private val args: MutableList<ShellArgument> = mutableListOf()
  private val additionalSuggestions: MutableList<ShellSuggestion> = mutableListOf()

  override fun subcommand(vararg names: String, content: (SubcommandContext.() -> Unit)?) {
    val context = SubcommandContextImpl(names.asList())
    content?.invoke(context)
    subcommands.add(context.build())
  }

  override fun option(vararg names: String, content: (OptionContext.() -> Unit)?) {
    val context = OptionContextImpl(names.asList())
    content?.invoke(context)
    options.add(context.build())
  }

  override fun argument(displayName: String, isOptional: Boolean, content: (ArgumentContext.() -> Unit)?) {
    val context = ArgumentContextImpl(displayName, isOptional)
    content?.invoke(context)
    args.add(context.build())
  }

  override fun additionalSuggestion(suggestion: ShellSuggestion) {
    additionalSuggestions.add(suggestion)
  }

  fun build(): ShellCommand {
    if (names.isEmpty()) error("At least one name must be provided")
    return ShellCommand(
      names = names,
      requiresSubcommand = requiresSubcommand,
      subcommands = subcommands,
      options = options,
      args = args,
      additionalSuggestions = additionalSuggestions,
      parserDirectives = parserDirectives,
      displayName = displayName,
      insertValue = insertValue,
      description = description,
      priority = priority,
      hidden = hidden
    )
  }
}

private class OptionContextImpl(private val names: List<String>) : OptionContext, BaseCommandPartContextImpl() {
  override var isPersistent: Boolean = false
  override var isRequired: Boolean = false
  override var separator: String? = null
  override var repeatTimes: Int = 1

  private val args: MutableList<ShellArgument> = mutableListOf()
  private val exclusiveOn: MutableList<String> = mutableListOf()
  private val dependsOn: MutableList<String> = mutableListOf()

  override fun argument(name: String, isOptional: Boolean, content: (ArgumentContext.() -> Unit)?) {
    val context = ArgumentContextImpl(name, isOptional)
    content?.invoke(context)
    args.add(context.build())
  }

  override fun exclusiveOn(vararg options: String) {
    exclusiveOn.addAll(options)
  }

  override fun dependsOn(vararg options: String) {
    dependsOn.addAll(options)
  }

  fun build(): ShellOption {
    if (names.isEmpty()) error("At least one name must be provided")
    return ShellOption(
      names = names,
      args = args,
      isPersistent = isPersistent,
      isRequired = isRequired,
      separator = separator,
      repeatTimes = repeatTimes,
      exclusiveOn = exclusiveOn,
      dependsOn = dependsOn,
      displayName = displayName,
      insertValue = insertValue,
      description = description,
      priority = priority,
      hidden = hidden
    )
  }
}

private class ArgumentContextImpl(private val displayName: String, private val isOptional: Boolean) : ArgumentContext {
  override var description: String? = null
  override var isVariadic: Boolean = false
  override var optionsCanBreakVariadicArg: Boolean = true
  override var isCommand: Boolean = false
  override var default: String? = null

  private val suggestions: MutableList<ShellSuggestion> = mutableListOf()
  private val templates: MutableList<String> = mutableListOf()
  private val generators: MutableList<ShellSuggestionsGenerator> = mutableListOf()

  override fun suggestion(value: ShellSuggestion) {
    suggestions.add(value)
  }

  override fun suggestions(vararg values: String) {
    suggestions.add(ShellSuggestion(names = values.asList()))
  }

  override fun templates(vararg values: String) {
    templates.addAll(values)
  }

  override fun generator(value: ShellSuggestionsGenerator) {
    generators.add(value)
  }

  fun build(): ShellArgument {
    return ShellArgument(
      displayName = displayName,
      description = description,
      suggestions = suggestions,
      templates = templates,
      generators = generators,
      isVariadic = isVariadic,
      optionsCanBreakVariadicArg = optionsCanBreakVariadicArg,
      isOptional = isOptional,
      isCommand = isCommand,
      default = default
    )
  }
}