// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.startup

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Allows modifying options used to start a shell session.
 *
 * Register the implementation as `org.jetbrains.plugins.terminal.shellExecOptionsCustomizer`
 * extension in `plugin.xml` file.
 */
@ApiStatus.Experimental
interface ShellExecOptionsCustomizer {
  /**
   * Customizes the environment variables of a new shell session.
   * The method is called on a background thread without a read action.
   *
   * @param shellExecOptions the parameters used to execute the shell session
   */
  @RequiresBackgroundThread(generateAssertion = false)
  @RequiresReadLockAbsence(generateAssertion = false)
  fun customizeExecOptions(project: Project, shellExecOptions: MutableShellExecOptions) {}

  /**
   * Customizes the default start working directory for the given project.
   * It serves as a default value for the "Start directory" field in "Settings | Tools | Terminal".
   * The value of this field determines the working directory for new shell sessions.
   * 
   * The method can be called on any thread without a read action.
   *
   * @return the starting directory, or `null` to use the default start working directory
   */
  fun getDefaultStartWorkingDirectory(project: Project): Path? = null

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<ShellExecOptionsCustomizer> =
      ExtensionPointName("org.jetbrains.plugins.terminal.shellExecOptionsCustomizer")
  }
}
