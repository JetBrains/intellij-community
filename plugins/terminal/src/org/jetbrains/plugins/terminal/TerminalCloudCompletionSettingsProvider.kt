// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import org.jetbrains.annotations.ApiStatus

/**
 * EP for supplying cloud completion settings to the terminal settings page.
 */
@ApiStatus.Internal
interface TerminalCloudCompletionSettingsProvider {
  fun addSettingsRow(panel: Panel): Row?

  companion object {
    private val EP_NAME = ExtensionPointName<TerminalCloudCompletionSettingsProvider>("org.jetbrains.plugins.terminal.terminalCloudCompletionSettingsProvider")

    internal fun getProvider(): TerminalCloudCompletionSettingsProvider? {
      return EP_NAME.extensionList.firstOrNull()
    }
  }
}