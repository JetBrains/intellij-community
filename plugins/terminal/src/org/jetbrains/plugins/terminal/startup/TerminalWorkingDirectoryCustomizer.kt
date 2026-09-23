// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.startup

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
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
   * @return the NIO Path of starting directory, or `null` to not provide any customization
   * and use value provided by the default implementation.
   * **Note that in RemDev case, NIO Path should point to the remote (host) machine, not the local (frontend) one**
   */
  suspend fun getDefaultStartWorkingDirectory(project: Project): Path?

  companion object {
    internal val EP_NAME: ExtensionPointName<TerminalWorkingDirectoryCustomizer> =
      ExtensionPointName("org.jetbrains.plugins.terminal.workingDirectoryCustomizer")
  }
}
