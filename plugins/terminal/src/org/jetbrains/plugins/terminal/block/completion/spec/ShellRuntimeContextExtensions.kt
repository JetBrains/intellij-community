// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellSuggestionType
import org.jetbrains.annotations.ApiStatus
import java.io.File

@get:ApiStatus.Experimental
val ShellRuntimeContext.project: Project
  get() = getUserData(PROJECT_KEY) ?: error("No project data in $this")

internal val PROJECT_KEY: Key<Project> = Key.create("Project")

/**
 * Returns the list of file suggestions based on child files of [path].
 * [path] can be either an absolute path or relative path.
 * In case of relative path, it is related to [ShellRuntimeContext.currentDirectory].
 *
 * Use [ShellDataGenerators.getParentPath] utility to get the right [path] from the user typed prefix.
 */
@ApiStatus.Experimental
suspend fun ShellRuntimeContext.getFileSuggestions(
  path: String,
  onlyDirectories: Boolean = false
): List<ShellCompletionSuggestion> {
  val adjustedPath = path.ifEmpty { "." }
  val result = runShellCommand("__jetbrains_intellij_get_directory_files $adjustedPath")
  if (result.exitCode != 0) {
    logger<ShellRuntimeContext>().warn("Get files command for path '$adjustedPath' failed with exit code ${result.exitCode}, output: ${result.output}")
    return emptyList()
  }
  val separator = File.separatorChar
  return result.output.splitToSequence("\n")
    .filter { !onlyDirectories || it.endsWith(separator) }
    // do not suggest './' and '../' directories if the user already typed some path
    .filter { path.isEmpty() || (it != ".$separator" && it != "..$separator") }
    .map {
      val type = if (it.endsWith(separator)) ShellSuggestionType.FOLDER else ShellSuggestionType.FILE
      ShellCompletionSuggestion(it, type)
    }
    .toList()
}