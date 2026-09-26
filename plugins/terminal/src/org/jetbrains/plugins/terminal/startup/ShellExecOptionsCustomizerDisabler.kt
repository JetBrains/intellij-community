// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.startup

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Allows disabling all [ShellExecOptionsCustomizer]s and [org.jetbrains.plugins.terminal.LocalTerminalCustomizer]s.
 * This can be useful, e.g., to make sure the terminal launched in IDE has the same PATH as OS's native terminal.
 * 
 * **Note that in the case of Remote Dev, customization disablers are executed on the backend,
 * so it is expected that implementations are registered either in the shared or the backend part of the plugin.**
 */
@ApiStatus.Internal
interface ShellExecOptionsCustomizerDisabler {
  fun shouldDisable(project: Project): Boolean

  companion object {
    @ApiStatus.Internal
    @JvmField
    val EP_NAME: ExtensionPointName<ShellExecOptionsCustomizerDisabler> =
      ExtensionPointName("org.jetbrains.plugins.terminal.shellExecOptionsCustomizerDisabler")
  }
}
