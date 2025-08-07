// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.session.ShellIntegrationFunctions.GET_DIRECTORY_FILES
import java.io.File

@get:ApiStatus.Experimental
val ShellRuntimeContext.project: Project
  get() = getUserData(PROJECT_KEY) ?: error("No project data in $this")

@ApiStatus.Internal
val PROJECT_KEY: Key<Project> = Key.create("Project")

@get:ApiStatus.Experimental
val ShellRuntimeContext.isReworkedTerminal: Boolean
  get() = getUserData(IS_REWORKED_KEY) ?: false

internal val IS_REWORKED_KEY: Key<Boolean> = Key.create("isReworked")

/**
 * Returns the list of [path] child file names.
 * [path] can be either an absolute path or relative path.
 * In case of relative path, it is related to [ShellRuntimeContext.currentDirectory].
 *
 * Use [ShellDataGenerators.getParentPath] utility to get the right [path] from the user typed prefix.
 */
@ApiStatus.Experimental
suspend fun ShellRuntimeContext.getChildFiles(
  path: String,
  onlyDirectories: Boolean = false,
): List<String> {
  val adjustedPath = path.ifEmpty { "." }
  val command = if (isReworkedTerminal) {
    "ls -1ap $adjustedPath"
  }
  else {
    "${GET_DIRECTORY_FILES.functionName} $adjustedPath"
  }
  val result = runShellCommand(command)
  if (result.exitCode != 0) {
    logger<ShellRuntimeContext>().warn("Get files command for path '$adjustedPath' failed with exit code ${result.exitCode}, output: ${result.output}")
    return emptyList()
  }
  val separator = File.separatorChar
  return result.output.splitToSequence("\n")
    .filter { it.isNotBlank() }
    .filter { !onlyDirectories || it.endsWith(separator) }
    // do not suggest './' and '../' directories if the user already typed some path
    .filter { path.isEmpty() || (it != ".$separator" && it != "..$separator") }
    .toList()
}
