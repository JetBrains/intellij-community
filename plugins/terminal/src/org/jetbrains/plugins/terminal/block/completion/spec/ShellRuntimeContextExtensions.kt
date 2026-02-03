// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.terminal.completion.spec.ShellFileInfo
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil.throwUnsupportedInExpTerminalException
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil.toShellFileInfo
import org.jetbrains.plugins.terminal.block.session.ShellIntegrationFunctions.GET_DIRECTORY_FILES
import java.io.File
import java.nio.file.InvalidPathException
import kotlin.io.path.Path

@ApiStatus.Internal
val PROJECT_KEY: Key<Project> = Key.create("Project")

@ApiStatus.Internal
val EEL_DESCRIPTOR_KEY: Key<EelDescriptor> = Key.create("EelDescriptor")

@ApiStatus.Internal
val IS_REWORKED_KEY: Key<Boolean> = Key.create("isReworked")

@get:ApiStatus.Experimental
val ShellRuntimeContext.project: Project
  get() = getUserData(PROJECT_KEY) ?: error("No project data in $this")

/**
 * EelDescriptor represents the environment where the shell session is running
 * and provides access to it through EEL API.
 */
@get:ApiStatus.Experimental
val ShellRuntimeContext.eelDescriptor: EelDescriptor
  get() = getUserData(EEL_DESCRIPTOR_KEY) ?: throwUnsupportedInExpTerminalException()

/**
 * The same as [ShellRuntimeContext.currentDirectory] but parsed as [EelPath]
 * in the environment if [eelDescriptor].
 */
@get:ApiStatus.Experimental
val ShellRuntimeContext.currentDirectoryPath: EelPath
  get() = EelPath.parse(currentDirectory, eelDescriptor)

/**
 * Currently, popup-completion is supported in both Experimental 2024 and Reworked terminals.
 * So, this method helps to adjust the behavior that depends on the terminal implementation.
 */
@get:ApiStatus.Experimental
val ShellRuntimeContext.isReworkedTerminal: Boolean
  get() = getUserData(IS_REWORKED_KEY) ?: false

/**
 * Returns the list of [path] child file names.
 * [path] can be either an absolute path, relative path, or home-relative path like `~/abc`.
 * In case of the relative path, it is related to [ShellRuntimeContext.currentDirectory].
 *
 * Use [ShellDataGenerators.getParentPath] utility to get the right [path] from the user typed prefix.
 */
@ApiStatus.Experimental
suspend fun ShellRuntimeContext.getChildFiles(
  path: String,
  onlyDirectories: Boolean = false,
): List<ShellFileInfo> {
  if (isReworkedTerminal) {
    return getChildFilesReworked(path, onlyDirectories)
  }
  else {
    return getChildFilesExp(path, onlyDirectories)
  }
}

private suspend fun ShellRuntimeContext.getChildFilesReworked(path: String, onlyDirectories: Boolean): List<ShellFileInfo> {
  check(isReworkedTerminal)

  val adjustedPath = OSAgnosticPathUtil.expandUserHome(path)
  val absolutePath = try {
    val nioPath = Path(adjustedPath)
    if (nioPath.isAbsolute) {
      nioPath.toString()
    }
    else {
      Path(currentDirectory, path).toString()
    }
  }
  catch (_: InvalidPathException) {
    return emptyList()
  }

  val files = listDirectoryFiles(absolutePath)
  return files.asSequence()
    .filter { !onlyDirectories || it.type == ShellFileInfo.Type.DIRECTORY }
    .toList()
}

private suspend fun ShellRuntimeContext.getChildFilesExp(path: String, onlyDirectories: Boolean): List<ShellFileInfo> {
  check(!isReworkedTerminal)

  val adjustedPath = path.ifEmpty { "." }
  val command = "${GET_DIRECTORY_FILES.functionName} $adjustedPath"
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
    .map { it.toShellFileInfo(separator) }
    .toList()
}
