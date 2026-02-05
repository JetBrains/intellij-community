// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.startup

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Allows modifying environment variables for starting shell in the remote environment [eelDescriptor].
 * Also, allows accessing all other options, like working directory, command.
 * 
 * @see ShellExecOptionsCustomizer
 */
@ApiStatus.Experimental
sealed interface MutableShellExecOptions {
  /**
   * Sets an environment variable with the given name and value.
   * 
   * The value should be in the format understood by the remote environment [eelDescriptor].
   * If the value is a file/directory, consider using methods converting the path automatically:
   * * [setEnvironmentVariableToPath]
   * * [appendEntryToPATH] / [prependEntryToPATH]
   * * [appendEntryToPathLikeEnv] / [prependEntryToPathLikeEnv]
   * 
   * The change is immediately reflected in [envs].
   * 
   * @param name The name of the environment variable to set.
   * @param value The value of the environment variable to set. It should be in the format
   *              understood by the remote environment [eelDescriptor].
   *              If null, the variable will be removed.
   */
  fun setEnvironmentVariable(name: String, value: String?)

  /**
   * Sets an environment variable to a file/directory path.
   *
   * The provided [path] is converted to the format understood by the remote environment
   * [eelDescriptor] before being stored in [envs].
   *
   * If the path cannot be converted, the environment variable remains unchanged.
   * Otherwise, the change is immediately reflected in [envs].
   *
   * @param name The name of the environment variable to set (for example, `JAVA_HOME` / `GOROOT`).
   * @param path An absolute path to set as the variable value, or `null` to remove the variable
   */
  fun setEnvironmentVariableToPath(name: String, path: Path?)

  /**
   * Appends the provided path entry to the PATH environment variable.
   *
   * The specified [entry] is converted to the format understood by the remote environment 
   * before being appended to the PATH.
   * 
   * If the path cannot be converted, the environment variable remains unchanged.
   * Otherwise, the change is immediately reflected in [envs].
   *
   * @param entry The path entry to append to the PATH environment variable.
   */
  fun appendEntryToPATH(entry: Path)

  /**
   * Prepends the provided path entry to the PATH environment variable.
   *
   * The specified [entry] is converted to the format understood by the remote environment 
   * before being prepended to the PATH.
   * 
   * If the path cannot be converted, the environment variable remains unchanged.
   * Otherwise, the change is immediately reflected in [envs].
   *
   * @param entry The path entry to prepend to the PATH environment variable.
   */
  fun prependEntryToPATH(entry: Path)

  /**
   * Appends the given path entry to the specified environment variable, treating it as a PATH-like variable.
   * Entries in the PATH-like variable are separated by ':' on Unix and ';' on Windows (`eelDescriptor.osFamily.pathSeparator`).
   * 
   * The provided [entry] is converted to the format understood by the remote environment before being appended.
   * 
   * If the path cannot be converted, the environment variable remains unchanged.
   * Otherwise, the change is immediately reflected in [envs].
   *
   * @param envName The name of the environment variable to modify.
   * @param entry The path entry to append to the specified environment variable.
   */
  fun appendEntryToPathLikeEnv(envName: String, entry: Path)

  /**
   * Prepend the given path entry to the specified environment variable, treating it as a PATH-like variable.
   * Entries in the PATH-like variable are separated by ':' on Unix and ';' on Windows (`eelDescriptor.osFamily.pathSeparator`).
   * 
   * The provided [entry] is converted to the format understood by the remote environment before being prepended.
   * 
   * If the path cannot be converted, the environment variable remains unchanged.
   * Otherwise, the change is immediately reflected in [envs].
   *
   * @param envName The name of the environment variable to modify.
   * @param entry The path entry to prepend to the specified environment variable.
   */
  fun prependEntryToPathLikeEnv(envName: String, entry: Path)

  /**
   * The environment where the shell will be started (local/WSL/Docker/SSH).
   */
  val eelDescriptor: EelDescriptor

  /**
   * The command describing how a shell process should be started.
   * It's in the format understood by the remote environment [eelDescriptor].
   */
  val execCommand: ShellExecCommand

  /**
   * Sets the command for starting a shell process.
   * The change is immediately reflected in [execCommand].
   * 
   * This method exists solely for compatibility with the deprecated
   * [org.jetbrains.plugins.terminal.LocalTerminalCustomizer.customizeCommandAndEnvironment].
   * However, it looks like it is an unneeded API that will be removed in future versions.
   */
  @ApiStatus.Internal
  fun setExecCommand(execCommand: ShellExecCommand)

  /**
   * The directory in which the shell will be started.
   * It's guaranteed that `workingDirectory.descriptor == eelDescriptor`.
   */
  val workingDirectory: EelPath

  /**
   * Read-only view of environment variables that will be used to spawn a shell process.
   * Values are in the format understood by the remote environment [eelDescriptor].
   * 
   * To modify the environment variables, use
   * * [setEnvironmentVariable] / [setEnvironmentVariableToPath]
   * * [appendEntryToPATH] / [prependEntryToPATH]
   * * [appendEntryToPathLikeEnv] / [prependEntryToPathLikeEnv]
   */
  val envs: Map<String, String>
}
