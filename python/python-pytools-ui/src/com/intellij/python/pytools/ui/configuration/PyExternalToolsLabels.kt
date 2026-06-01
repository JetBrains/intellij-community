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
 * Per-step availability marker for one chain mode. UNKNOWN suppresses the glyph entirely
 * (initial render before probes complete). PARTIAL is only meaningful for the SDK step (some
 * project SDKs have the tool, others don't); Path and uvx are binary FOUND / NOT_FOUND.
 */
internal enum class ChainStepStatus { UNKNOWN, FOUND, PARTIAL, NOT_FOUND }

internal fun SdkAvailability?.toChainStatus(): ChainStepStatus = when {
  this == null -> ChainStepStatus.UNKNOWN
  totalCount == 0 -> ChainStepStatus.NOT_FOUND
  matchedCount == 0 -> ChainStepStatus.NOT_FOUND
  matchedCount == totalCount -> ChainStepStatus.FOUND
  else -> ChainStepStatus.PARTIAL
}

internal fun PathFieldValue?.toChainStatus(): ChainStepStatus = when (this) {
  null -> ChainStepStatus.UNKNOWN
  is PathFieldValue.Custom -> ChainStepStatus.FOUND
  is PathFieldValue.AutoDetected -> ChainStepStatus.FOUND
  PathFieldValue.NotFound -> ChainStepStatus.NOT_FOUND
}

internal fun uvxChainStatus(uvAvailable: Boolean?): ChainStepStatus = when (uvAvailable) {
  null -> ChainStepStatus.UNKNOWN
  true -> ChainStepStatus.FOUND
  false -> ChainStepStatus.NOT_FOUND
}

/**
 * Render the picked [mode] followed by its implicit fallback chain — e.g. picking `Sdk` becomes
 * `Sdk · Path · uvx`, `Path` becomes `Path · uvx`, and `uvx` is just `uvx`. Each chain step is
 * decorated with a coloured ✓ / ✗ / ◐ glyph from [statusFor]: ✓ when the step would resolve,
 * ✗ when it can't, ◐ when only some project SDKs have the tool. Steps returning
 * [ChainStepStatus.UNKNOWN] render plain (initial state before background probes complete).
 */
@Nls
@Suppress("HardCodedStringLiteral")
internal fun modeChainLabel(
  mode: ExecutableDiscoveryMode,
  statusFor: ((ExecutableDiscoveryMode) -> ChainStepStatus)? = null,
): String {
  val all = ExecutableDiscoveryMode.entries
  val tail = all.subList(all.indexOf(mode) + 1, all.size)
  val chain = listOf(mode) + tail
  if (statusFor == null) {
    // No row context (e.g. popup items) — render the plain, undecorated chain.
    return chain.joinToString(" · ") { modeLabel(it) }
  }
  return buildString {
    append("<html>")
    chain.joinTo(this, " · ") { m -> modeLabel(m) + glyphSuffix(statusFor(m)) }
    append("</html>")
  }
}

/**
 * Render the trailing glyph for one chain step. Covers every [ChainStepStatus] — including
 * UNKNOWN, which renders as a muted `?` so the user sees "probing in progress" rather than
 * a silently-missing marker.
 */
private fun glyphSuffix(status: ChainStepStatus): String {
  val (glyph, color) = when (status) {
    ChainStepStatus.UNKNOWN -> "?" to STEP_UNKNOWN_COLOR
    ChainStepStatus.FOUND -> "✓" to STEP_FOUND_COLOR
    ChainStepStatus.PARTIAL -> "◐" to STEP_PARTIAL_COLOR
    ChainStepStatus.NOT_FOUND -> "✗" to STEP_NOT_FOUND_COLOR
  }
  val hex = "%06x".format(color.rgb and 0xFFFFFF)
  return "<font color='#$hex'>&nbsp;$glyph</font>"
}

private val STEP_FOUND_COLOR: Color = JBColor(Color(0x59A869), Color(0x6A8759))
private val STEP_NOT_FOUND_COLOR: Color = JBColor(Color(0xC75450), Color(0xCF5B56))
private val STEP_PARTIAL_COLOR: Color = JBColor(
  JBColor.namedColor("ColorPalette.Orange6", Color(0xE08855)),
  JBColor.namedColor("ColorPalette.Orange4", Color(0xCB7B57)),
)
private val STEP_UNKNOWN_COLOR: Color = JBColor.GRAY
