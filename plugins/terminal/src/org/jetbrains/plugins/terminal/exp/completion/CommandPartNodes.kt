// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

internal abstract class CommandPartNode<T>(val text: String, open val spec: T?, val parent: CommandPartNode<*>?) {
  val children: MutableList<CommandPartNode<*>> = mutableListOf()

  open fun getSuggestionsOfNext(): List<BaseSuggestion> = emptyList()

  override fun toString(): String {
    return "${javaClass.simpleName} { text: $text, children: $children }"
  }
}

internal class SubcommandNode(text: String,
                              override val spec: ShellSubcommand,
                              parent: CommandPartNode<*>?) : CommandPartNode<ShellSubcommand>(text, spec, parent) {
  override fun getSuggestionsOfNext(): List<BaseSuggestion> {
    val suggestions = mutableListOf<BaseSuggestion>()
    if (children.isEmpty()) {
      suggestions.addAll(spec.subcommands)
    }

    if (spec.requiresSubcommand) {
      return suggestions
    }

    val existingOptions = children.mapNotNull { (it as? OptionNode)?.spec }
    val options = getAllAvailableOptions().filter { opt ->
      (opt.repeatTimes == 0 || existingOptions.count { it == opt } < opt.repeatTimes)
      && opt.exclusiveOn.none { exclusive -> existingOptions.any { it.names.contains(exclusive) } }
      && opt.dependsOn.all { dependant -> existingOptions.any { it.names.contains(dependant) } }
    }
    suggestions.addAll(options)

    val args = spec.args
    val existingArgsCount = children.count { it is ArgumentNode }
    if (args.size > existingArgsCount) {
      // TODO: here should be also file suggestions if there are corresponding argument
      suggestions.addAll(args.flatMap { it.getArgumentSuggestions() })
    }
    return suggestions
  }

  private fun getAllAvailableOptions(): List<ShellOption> {
    val options = mutableListOf<ShellOption>()
    options.addAll(spec.options)
    var cur = parent
    while (cur is SubcommandNode) {
      // parent commands can define 'persistent' options - they can be used in all nested subcommands
      options.addAll(cur.spec.options.filter { it.isPersistent })
      cur = cur.parent
    }
    return options
  }
}

internal class OptionNode(text: String,
                          override val spec: ShellOption,
                          parent: CommandPartNode<*>?) : CommandPartNode<ShellOption>(text, spec, parent) {
  override fun getSuggestionsOfNext(): List<BaseSuggestion> {
    val suggestions = mutableListOf<BaseSuggestion>()
    suggestions.addAll(getDirectSuggestionsOfNext())
    if (parent is SubcommandNode && spec.args.all { it.isOptional }) {
      suggestions.addAll(parent.getSuggestionsOfNext())
    }
    return suggestions
  }

  fun getDirectSuggestionsOfNext(): List<BaseSuggestion> {
    val args = spec.args
    val existingArgsCount = children.count { it is ArgumentNode }
    if (args.size > existingArgsCount) {
      // TODO: here should be also file suggestions if there are corresponding argument
      return args.flatMap { it.getArgumentSuggestions() }
    }
    return emptyList()
  }
}

internal class ArgumentNode(text: String,
                            override val spec: ShellArgument,
                            parent: CommandPartNode<*>?) : CommandPartNode<ShellArgument>(text, spec, parent) {
  override fun getSuggestionsOfNext(): List<BaseSuggestion> {
    return parent?.getSuggestionsOfNext() ?: emptyList()
  }
}

internal class UnknownNode(text: String, parent: CommandPartNode<*>?) : CommandPartNode<Any>(text, null, parent)

private fun ShellArgument.getArgumentSuggestions(): List<ShellArgumentSuggestion> {
  return suggestions.map { ShellArgumentSuggestion(it, this) }
}