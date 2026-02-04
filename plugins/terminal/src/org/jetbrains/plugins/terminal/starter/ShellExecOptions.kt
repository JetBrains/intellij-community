// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.starter

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Represents options for executing shell in a specific environment.
 */
@ApiStatus.Experimental
interface ShellExecOptions {

  /**
   * The command describing how shell should be started.
   */
  var execCommand: ShellExecCommand

  /**
   * The directory in which the shell will be started.
   */
  val workingDirectory: EelPath

  /**
   * The environment where the shell will be started (local/WSL/Docker/SSH).
   */
  val eelDescriptor: EelDescriptor

  /**
   * Read-only view of environment variables that will be used to spawn the shell process.
   * Values are in the format understood by the remote environment [eelDescriptor].
   */
  val envs: Map<String, String>

  /**
   * Sets an environment variable with the given name and value.
   * 
   * For known environment variables containing paths (e.g., PATH), local paths are
   * automatically translated to paths understood by the remote environment.
   * 
   * For explicit path translation, use [setEnvironmentVariableToPath], [appendEntryToPATH],
   * [prependEntryToPATH], [appendEntryToPathLikeEnv], [prependEntryToPathLikeEnv] instead.
   * 
   * @param name The name of the environment variable to set.
   * @param value The value to set for the environment variable in the format understood by the remote environment [eelDescriptor].
   *              If null, the variable will be removed.
   */
  fun setEnvironmentVariable(name: String, value: String?)

  /**
   * Sets an environment variable to a file/directory path.
   *
   * The provided [path] is an absolute path local to the IDE and is converted to the
   * path understood by the target environment ([eelDescriptor]) before being stored in [envs].
   *
   * If [path] is `null`, the variable is removed from the environment.
   *
   * If the path cannot be converted to a valid path for the target environment (for example, it is
   * not absolute or conversion fails), this method does not update the variable.
   *
   * @param name the name of the environment variable to set (for example, `JAVA_HOME` / `GOROOT`).
   * @param path an absolute path to set as the variable value, or `null` to remove the variable.
   */
  fun setEnvironmentVariableToPath(name: String, path: Path?)

  fun appendEntryToPATH(pathEntry: Path)
  fun prependEntryToPATH(pathEntry: Path)

  fun appendEntryToPathLikeEnv(envName: String, entryPath: Path)
  fun prependEntryToPathLikeEnv(envName: String, entryPath: Path)
}
