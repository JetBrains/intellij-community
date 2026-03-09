// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.terminal.TerminalTitle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Builds a title string taking into account the "Show Application Title" Terminal setting.
 */
@ApiStatus.Experimental
fun TerminalTitle.buildSettingsAwareTitle(): @Nls String {
  return buildTitle(ignoreAppTitle = !AdvancedSettings.getBoolean("terminal.show.application.title"))
}

/**
 * Builds a title string taking into account the "Show Application Title" Terminal setting.
 */
@ApiStatus.Experimental
fun TerminalTitle.buildSettingsAwareFullTitle(): @Nls String {
  return buildFullTitle(ignoreAppTitle = !AdvancedSettings.getBoolean("terminal.show.application.title"))
}