// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import com.intellij.terminal.completion.spec.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.block.completion.ShellCommandSpecsManagerImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellCommandSpecImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellEnvBasedGenerators
import java.io.File

@ApiStatus.Experimental
object ShellDataGenerators {
  /**
   * Provides the file suggestions for the current [typed prefix][com.intellij.terminal.completion.spec.ShellRuntimeContext.typedPrefix].
   * If typed prefix is a relative path,
   * then suggestions are related to the [current shell directory][com.intellij.terminal.completion.spec.ShellRuntimeContext.currentDirectory].
   * For example,
   * 1. Typed prefix: `resources/img/ico`, then the files will be provided from `<current directory absolute path>/resources/img/` directory.
   * 2. Typed prefix: `/usr/bin/ab`, then the files will be provided from `/user/bin/` directory.
   *
   * This generator is caching the results based on the [typed prefix][com.intellij.terminal.completion.spec.ShellRuntimeContext.typedPrefix].
   *
   * Note that the suggestions are not filtered by the file prefix extracted from the [typed prefix][com.intellij.terminal.completion.spec.ShellRuntimeContext.typedPrefix].
   * All the files from the base directory extracted from the typed prefix are returned as [ShellCompletionSuggestion]'s.
   * All prefix matching logic is performed by the core completion logic.
   */
  fun fileSuggestionsGenerator(onlyDirectories: Boolean = false): ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>> {
    val key = if (onlyDirectories) "directories" else "files"
    return ShellRuntimeDataGenerator(
      debugName = key,
      getCacheKey = { "$key:${getParentPath(it.typedPrefix, it.shellName)}" }
    ) { context ->
      val path = getParentPath(context.typedPrefix, context.shellName)
      val files: List<String> = context.getChildFiles(path, onlyDirectories)
      val prefixReplacementIndex = path.length + if (context.typedPrefix.startsWith('"')) 1 else 0
      val suggestions = files.flatMap {
        val type = if (it.endsWith(File.separatorChar)) ShellSuggestionType.FOLDER else ShellSuggestionType.FILE
        val suggestion = ShellCompletionSuggestion(name = it, type = type, prefixReplacementIndex = prefixReplacementIndex)
        if (type == ShellSuggestionType.FILE) {
          listOf(suggestion)
        }
        else {
          // Directory suggestion has a trailing file separator, but suggestion without it is also valid.
          // It is needed for the parser to consider it as a valid suggestion and not mark it as something unknown.
          val hiddenSuggestion = ShellCompletionSuggestion(
            name = it.removeSuffix(File.separator),
            type = ShellSuggestionType.FOLDER,
            prefixReplacementIndex = prefixReplacementIndex,
            isHidden = true
          )
          listOf(suggestion, hiddenSuggestion)
        }
      }
      val adjustedPrefix = context.typedPrefix.removePrefix("\"").removeSuffix("'")
      // If the base path is the same as the typed prefix, then add an empty suggestion.
      // Because the current typed prefix is already a valid value of the file argument.
      // It is needed for the parser to consider current typed prefix as a valid file suggestion.
      if (path.isNotEmpty() && path == adjustedPrefix) {
        val emptySuggestion = ShellCompletionSuggestion(name = "", prefixReplacementIndex = prefixReplacementIndex, isHidden = true)
        suggestions + emptySuggestion
      }
      else suggestions
    }
  }

  /**
   * Provides the list of all available commands, functions, keywords and aliases available in the Shell.
   * Useful for the commands that accept the other shell command as an argument.
   * `sudo` command is the most popular example: it accepts the argument that is a separate shell command.
   *
   * This generator is caching the results.
   */
  fun availableCommandsGenerator(): ShellRuntimeDataGenerator<List<ShellCommandSpec>> {
    return ShellRuntimeDataGenerator(cacheKeyAndDebugName = "commands") { context ->
      val shellEnv = ShellEnvBasedGenerators.getShellEnv(context)
                     ?: return@ShellRuntimeDataGenerator emptyList()
      val commandSpecManager = ShellCommandSpecsManagerImpl.getInstance()
      val commands = sequence {
        yieldAll(shellEnv.keywords)
        yieldAll(shellEnv.builtins)
        yieldAll(shellEnv.functions)
        yieldAll(shellEnv.commands)
      }.map {
        commandSpecManager.getLightCommandSpec(it) ?: ShellCommandSpecImpl(it)
      }
      val aliases = shellEnv.aliases.asSequence().map { (alias, command) ->
        ShellCommandSpecImpl(name = alias, descriptionSupplier = TerminalBundle.messagePointer("doc.popup.alias.text", command))
      }
      // place aliases first, so the alias will have preference over the command, if there is the command with the same name
      (aliases + commands).distinctBy { it.name }.toList()
    }
  }

  fun <T> emptyListGenerator(): ShellRuntimeDataGenerator<List<T>> = ShellRuntimeDataGenerator(debugName = "empty list") { emptyList() }

  /**
   * Creates a cache key or debug name of the generator in the following format:
   * <command>.<subcommand> <suffix>
   * For example, `git.checkout branches` (generates branch names for checkout command of Git).
   * Subcommands can be absent, but the main command should be present.
   * @param [commandNames] hierarchy of the command names from main command name to subcommand. It describes what command generator belongs to.
   * @param [suffix] any string describing the meaning of the generator.
   */
  fun createCacheKey(commandNames: List<String>, suffix: String): String {
    assert(commandNames.isNotEmpty())
    return commandNames.joinToString(".") + " $suffix"
  }

  /**
   * Tries to guess the parent path of [typedPrefix] if it is a file path.
   * For example,
   * 1. `file.txt` -> `<empty>`
   * 2. `src/file.txt` -> `src/`
   * 3. `/usr/b` -> `/usr/`
   *
   * If [shellName] is PowerShell, then this function considers both slash `/` and backslash `\` as path separators.
   */
  fun getParentPath(typedPrefix: String, shellName: ShellName): String {
    val separator = File.separatorChar
    val pathSeparators = if (shellName.isPowerShell()) {
      val oppositeSeparator = if (separator == '/') '\\' else '/'
      charArrayOf(separator, oppositeSeparator)
    }
    else charArrayOf(separator)
    // Remove possible quotes before and after
    // TODO: quotes should not be handled there, typed prefix should already contain no quotes.
    val adjustedPrefix = typedPrefix.removePrefix("\"").removeSuffix("'")
    val lastSeparatorIndex = adjustedPrefix.lastIndexOfAny(pathSeparators)
    return if (lastSeparatorIndex != -1) {
      adjustedPrefix.substring(0, lastSeparatorIndex + 1)
    }
    else ""
  }
}
