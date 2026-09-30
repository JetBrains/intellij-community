// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.startup

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * **Note that in the case of Remote Dev, customizers are executed on the frontend,
 * so it is expected that implementations are registered either in the shared or the frontend part of the plugin.**
 *
 * Register the implementation as `org.jetbrains.plugins.terminal.workingDirectoryCustomizer`
 * extension in `plugin.xml` file.
 */
@ApiStatus.OverrideOnly
@ApiStatus.Experimental
interface TerminalWorkingDirectoryCustomizer {
  /**
   * Customizes the default start working directory for the given project.
   * It serves as a default value for the "Start directory" field in "Settings | Tools | Terminal".
   * The value of this field determines the working directory for new shell sessions.
   *
   * **This method is called only once and returned value is cached, so the result shouldn't depend on the variable project state.**
   *
   * @return the NIO Path of starting directory, or `null` to not provide any customization
   * and use value provided by the default implementation.
   * **Note that in RemDev case, NIO Path should point to the remote (host) machine, not the local (frontend) one**
   */
  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  fun getDefaultStartWorkingDirectory(project: Project): Path? = null

  /**
   * Customizes the working directory of a new terminal session.
   * The method is called only when a new session starts, and the caller has not requested a working directory.
   * The result takes priority over the "Start directory" setting.
   * This value is not shown anywhere in the UI.
   *
   * @return the NIO Path of starting directory, or `null` to not provide any customization
   * and use value provided by the default implementation.
   * **Note that in RemDev case, NIO Path should point to the remote (host) machine, not the local (frontend) one**
   */
  suspend fun getContextualStartWorkingDirectory(project: Project): Path? = null

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<TerminalWorkingDirectoryCustomizer> =
      ExtensionPointName("org.jetbrains.plugins.terminal.workingDirectoryCustomizer")
  }
}
