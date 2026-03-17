// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.terminal.TerminalTitle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.settings.TerminalApplicationTitleShowingMode

/**
 * Builds a title string taking into account the "Show Application Title" Terminal setting.
 *
 * @param isCommandRunning used for determining whether to show an application title when a command is running.
 * Taken into account only if "Show application title in tab name: when command is running" option is enabled
 * ([TerminalOptionsProvider.applicationTitleShowingMode]).
 */
@ApiStatus.Experimental
fun TerminalTitle.buildSettingsAwareTitle(isCommandRunning: Boolean = false): @Nls String {
  return buildTitle(ignoreAppTitle = !shouldShowAppTitle(isCommandRunning))
}

/**
 * Builds a title string taking into account the "Show Application Title" Terminal setting.
 *
 * @param isCommandRunning used for determining whether to show an application title when a command is running.
 * Taken into account only if "Show application title in tab name: when command is running" option is enabled
 * ([TerminalOptionsProvider.applicationTitleShowingMode]).
 */
@ApiStatus.Experimental
fun TerminalTitle.buildSettingsAwareFullTitle(isCommandRunning: Boolean = false): @Nls String {
  return buildFullTitle(ignoreAppTitle = !shouldShowAppTitle(isCommandRunning))
}

private fun shouldShowAppTitle(isCommandRunning: Boolean): Boolean {
  val options = TerminalOptionsProvider.instance
  return options.showApplicationTitle
         && (options.applicationTitleShowingMode == TerminalApplicationTitleShowingMode.WHEN_COMMAND_RUNNING && isCommandRunning
             || options.applicationTitleShowingMode == TerminalApplicationTitleShowingMode.ALWAYS)
}