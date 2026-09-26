// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.startup

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import org.jetbrains.annotations.ApiStatus

/**
 * Represents effectively immutable options for starting shell in the remote environment [eelDescriptor].
 * 
 * Note [MutableShellExecOptions] doesn't extend this interface to ensure
 * that instances of this interface remain effectively immutable.
 */
@ApiStatus.Internal
sealed interface ShellExecOptions {
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
   * The directory in which the shell will be started.
   * It's guaranteed that `workingDirectory.descriptor == eelDescriptor`.
   */
  val workingDirectory: EelPath

  /**
   * Read-only view of environment variables that will be used to spawn the shell process.
   * Values are in the format understood by the remote environment [eelDescriptor].
   */
  val envs: Map<String, String>
}
