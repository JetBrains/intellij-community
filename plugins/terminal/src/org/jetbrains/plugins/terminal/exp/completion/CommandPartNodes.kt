// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

internal abstract class CommandPartNode<T>(val text: String, open val spec: T?, val parent: CommandPartNode<*>?) {
  val children: MutableList<CommandPartNode<*>> = mutableListOf()

  open fun getSuggestionsOfNext(): List<BaseSuggestion> = emptyList()

  fun getAvailableArguments(allArgs: List<ShellArgument>): List<ShellArgument> {
    val existingArgs = children.mapNotNull { (it as? ArgumentNode)?.spec }
    val lastExistingArg = existingArgs.lastOrNull()
    val lastExistingArgIndex = lastExistingArg?.let { allArgs.indexOf(it) } ?: -1
    val firstRequiredArgIndex = allArgs.withIndex().find { (ind, argument) ->
      ind > lastExistingArgIndex && !argument.isOptional
    }?.index ?: (allArgs.size - 1)
    val includeLast = lastExistingArg?.isVariadic == true
    return allArgs.subList(lastExistingArgIndex + if (includeLast) 0 else 1, firstRequiredArgIndex + 1)
  }

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

    val lastArg = (children.lastOrNull() as? ArgumentNode)?.spec
    if ((!getMergedParserDirectives().optionsMustPrecedeArguments || children.filterIsInstance<ArgumentNode>().isEmpty())
        && (lastArg?.isVariadic != true || lastArg.optionsCanBreakVariadicArg)) {
      val options = getAvailableOptions()
      suggestions.addAll(options)
    }

    val availableArgs = getAvailableArguments(spec.args)
    //   TODO: here should be also file suggestions if there are corresponding argument
    suggestions.addAll(availableArgs.flatMap { it.getArgumentSuggestions() })
    return suggestions
  }

  private fun getAvailableOptions(): List<ShellOption> {
    val existingOptions = children.mapNotNull { (it as? OptionNode)?.spec }
    return getAllOptions().filter { opt ->
      (opt.repeatTimes == 0 || existingOptions.count { it == opt } < opt.repeatTimes)
      && opt.exclusiveOn.none { exclusive -> existingOptions.any { it.names.contains(exclusive) } }
      && opt.dependsOn.all { dependant -> existingOptions.any { it.names.contains(dependant) } }
    }
  }

  private fun getAllOptions(): List<ShellOption> {
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
                          parent: CommandPartNode<*>?) : CommandPartNode<ShellOption>(text, spec, parent) {
  override fun getSuggestionsOfNext(): List<BaseSuggestion> {
    val suggestions = mutableListOf<BaseSuggestion>()
    suggestions.addAll(getDirectSuggestionsOfNext())
    val availableArgs = getAvailableArguments(spec.args)
    val lastArg = (children.lastOrNull() as? ArgumentNode)?.spec
    // suggest parent options and args if there is no required args or last arg is required, but variadic
    val args = if (lastArg?.isVariadic == true && lastArg.optionsCanBreakVariadicArg) availableArgs - lastArg else availableArgs
    if (parent is SubcommandNode && args.all { it.isOptional }) {
      suggestions.addAll(parent.getSuggestionsOfNext())
    }
    return suggestions
  }

  fun getDirectSuggestionsOfNext(): List<BaseSuggestion> {
    val availableArgs = getAvailableArguments(spec.args)
    //   TODO: here should be also file suggestions if there are corresponding argument
    return availableArgs.flatMap { it.getArgumentSuggestions() }
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