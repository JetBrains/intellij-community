// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.classic

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.AbstractTerminalRunner

/**
 * Allows providing a custom [AbstractTerminalRunner] for the Classic Terminal instead of the
 * default [org.jetbrains.plugins.terminal.LocalTerminalDirectRunner].
 *
 * Providers are queried in registration order, the first available runner wins. If no provider is
 * applicable, [org.jetbrains.plugins.terminal.TerminalToolWindowManager] falls back to
 * [org.jetbrains.plugins.terminal.LocalTerminalDirectRunner].
 */
@ApiStatus.Internal
interface CustomTerminalRunnerProvider {
  fun createTerminalRunner(project: Project): AbstractTerminalRunner<*>

  companion object {
    @JvmStatic
    fun createRunner(project: Project): AbstractTerminalRunner<*>? {
      return EP_NAME.findFirstSafe { true }?.createTerminalRunner(project)
    }

    private val EP_NAME: ExtensionPointName<CustomTerminalRunnerProvider> =
      ExtensionPointName("org.jetbrains.plugins.terminal.customTerminalRunnerProvider")
  }
}