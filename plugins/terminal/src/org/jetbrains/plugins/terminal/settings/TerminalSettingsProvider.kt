// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.settings

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * Allows providing a UI component to the "Settings | Tools | Terminal" page.
 *
 * Register the implementation as `org.jetbrains.plugins.terminal.terminalSettingsProvider`
 * extension in `plugin.xml` file.
 */
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
interface TerminalSettingsProvider {
  /**
   * @return a new instance of [UnnamedConfigurable] to be added at the bottom of the Terminal settings page
   */
  @RequiresEdt(generateAssertion = false)
  fun createConfigurable(project: Project): UnnamedConfigurable?

  companion object {
    internal val EP_NAME: ExtensionPointName<TerminalSettingsProvider> =
      ExtensionPointName("org.jetbrains.plugins.terminal.terminalSettingsProvider")
  }
}
