// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui.configuration

import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import com.intellij.python.pytools.ui.PyToolsUiBundle
import com.intellij.ui.JBColor
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.UIManager

internal const val COL_ENABLED: Int = 0
internal const val COL_TOOL: Int = 1
internal const val COL_MODE: Int = 2
internal const val COL_PATH: Int = 3

/**
 * Foreground for the bracketed list of activated features in the Tool column. Uses the
 * "added / enabled" green (matching `FileStatus.ADDED`) so the readable contrast is consistent
 * across light and dark themes.
 */
internal val ENABLED_OPTIONS_FOREGROUND: Color =
  JBColor(Color(0x59A869), Color(0x6A8759))

/**
 * Foreground for the warning "No features selected" hint shown in the Tool column when a tool
 * is enabled but its features summary is empty. Uses an orange that matches the IDE's
 * inspection-warning palette across light and dark themes.
 */
internal val NO_FEATURES_FOREGROUND: Color =
  JBColor(
    JBColor.namedColor("ColorPalette.Orange6", Color(0xE08855)),
    JBColor.namedColor("ColorPalette.Orange4", Color(0xCB7B57)),
  )

/**
 * Border color for the search-hit spotlight, matching the IntelliJ Settings page's
 * search match indicator (see `com.intellij.openapi.options.ex.GlassPanel`).
 */
internal fun searchSpotlightBorderColor(): Color =
  UIManager.getColor("Settings.Spotlight.borderColor") ?: JBColor(
    JBColor.namedColor("ColorPalette.Orange6", 0xE08855),
    JBColor.namedColor("ColorPalette.Orange4", 0xA36B4E),
  )

@Nls
internal fun modeLabel(mode: ExecutableDiscoveryMode): String = when (mode) {
  ExecutableDiscoveryMode.INTERPRETER -> PyToolsUiBundle.message("settings.external.tools.mode.interpreter")
  ExecutableDiscoveryMode.PATH -> PyToolsUiBundle.message("settings.external.tools.mode.path")
  ExecutableDiscoveryMode.UVX -> PyToolsUiBundle.message("settings.external.tools.mode.uvx")
}

/**
 * Render the picked [mode] followed by its implicit fallback chain — e.g. picking `Sdk` becomes
 * `Sdk → Path → uvx`, `Path` becomes `Path → uvx`, and `uvx` is just `uvx`. Used by both the
 * combobox renderer (enabled rows) and the plain-label renderer (disabled rows) so the two paths
 * stay in lock-step.
 */
@Nls
internal fun modeChainLabel(mode: ExecutableDiscoveryMode): String {
  val all = ExecutableDiscoveryMode.entries
  val tail = all.subList(all.indexOf(mode) + 1, all.size)
  return (listOf(mode) + tail).joinToString(" → ") { modeLabel(it) }
}
