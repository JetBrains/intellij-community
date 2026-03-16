// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.startup

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.session.ShellName
import java.nio.file.Path

/**
 * Allows customizing the shell integration.
 */
@ApiStatus.Experimental
sealed interface ShellIntegrationConfigurer {
  /**
   * The name of the shell to be started.
   */
  val shellName: ShellName

  /**
   * Registers a shell script to be sourced at the shell startup.
   * The [shellScriptFile] must match the shell syntax (use [shellName] to detect the shell type).
   * 
   * The arguments are passed as is. Since execution happens
   * in the remote environment ([MutableShellExecOptions.eelDescriptor]),
   * the arguments must be in the format understood by that environment.
   *
   * @param shellScriptFile The absolute path to the shell script file.
   * @param args The arguments to be passed to the shell script.
   */
  fun sourceShellScriptAtShellStartup(shellScriptFile: Path, args: List<String> = emptyList())
}
