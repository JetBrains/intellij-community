// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.terminal.block.completion.spec.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellCommandSpecImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellEnvBasedGenerators
import org.jetbrains.plugins.terminal.exp.completion.IJShellCommandSpecsManager
import java.io.File

@ApiStatus.Experimental
object ShellDataGenerators {
  private val LOG: Logger = logger<ShellDataGenerators>()

  fun fileSuggestionsGenerator(onlyDirectories: Boolean = false): ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>> {
    fun getBasePath(context: ShellRuntimeContext): String {
      val separator = File.separatorChar
      val adjustedPrefix = context.typedPrefix.removePrefix("\"").removeSuffix("'")
      return if (adjustedPrefix.contains(separator)) {
        adjustedPrefix.substringBeforeLast(separator) + separator
      }
      else "."
    }

    return ShellRuntimeDataGenerator(
      debugName = "files",
      getCacheKey = { "files:${getBasePath(it)}" }
    ) { context ->
      val separator = File.separatorChar
      val basePath = getBasePath(context)
      val result = context.runShellCommand("__jetbrains_intellij_get_directory_files $basePath")
      if (result.exitCode != 0) {
        LOG.error("Get files command for path '$basePath' failed with exit code ${result.exitCode}, output: ${result.output}")
        return@ShellRuntimeDataGenerator emptyList()
      }
      result.output.splitToSequence("\n")
        .filter { !onlyDirectories || it.endsWith(separator) }
        // do not suggest './' and '../' directories if the user already typed some path
        .filter { basePath == "." || (it != ".$separator" && it != "..$separator") }
        .map {
          val type = if (it.endsWith(separator)) ShellSuggestionType.FOLDER else ShellSuggestionType.FILE
          ShellCompletionSuggestion(it, type)
        }
        // add an empty choice to be able to handle the case when the folder is chosen
        .let { if (basePath != ".") it.plus(ShellCompletionSuggestion("")) else it }
        .toList()
    }
  }

  fun availableCommandsGenerator(): ShellRuntimeDataGenerator<List<ShellCommandSpec>> {
    return ShellRuntimeDataGenerator(cacheKeyAndDebugName = "commands") { context ->
      val shellEnv = ShellEnvBasedGenerators.getShellEnv(context)
                     ?: return@ShellRuntimeDataGenerator emptyList()
      val commandSpecManager = IJShellCommandSpecsManager.getInstance()
      val commands = sequence {
        yieldAll(shellEnv.keywords)
        yieldAll(shellEnv.builtins)
        yieldAll(shellEnv.functions)
        yieldAll(shellEnv.commands)
      }.map {
        commandSpecManager.getLightCommandSpec(it) ?: ShellCommandSpecImpl(listOf(it))
      }
      val aliases = shellEnv.aliases.asSequence().map { (alias, command) ->
        ShellCommandSpecImpl(names = listOf(alias),
                             descriptionSupplier = TerminalBundle.messagePointer("doc.popup.alias.text", command))
      }
      // place aliases first, so the alias will have preference over the command, if there is the command with the same name
      (aliases + commands).distinctBy { it.names.single() }.toList()
    }
  }

  fun <T> emptyListGenerator(): ShellRuntimeDataGenerator<List<T>> = ShellRuntimeDataGenerator { emptyList() }

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
}