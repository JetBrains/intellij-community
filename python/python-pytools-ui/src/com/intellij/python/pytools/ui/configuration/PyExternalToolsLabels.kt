// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui.configuration

import com.intellij.python.pytools.ui.PyToolsUiBundle
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.UIManager

/**
 * Shared column geometry so the "Lookup" caption in the header bar lines up with the lookup-chain
 * text in every row: both lay their right side out as `[chain][gap][toggle column]`, right-anchored,
 * so the chain and caption share a right edge a fixed [columnGap] from the toggle regardless of the
 * chain's length.
 */
internal fun toggleColumnWidth(): Int = JBUI.scale(64)

internal fun columnGap(): Int = JBUI.scale(12)

/** Wrap [comp] in a panel pinned to [width], anchored (WEST by default) within that width. */
internal fun fixedWidthPanel(width: Int, comp: JComponent, anchor: String = BorderLayout.WEST): JComponent =
  object : JPanel(BorderLayout()) {
    init {
      isOpaque = false
      add(comp, anchor)
    }

    override fun getMinimumSize(): Dimension = Dimension(width, super.getMinimumSize().height)
    override fun getPreferredSize(): Dimension = Dimension(width, super.getPreferredSize().height)
    override fun getMaximumSize(): Dimension = Dimension(width, super.getMaximumSize().height)
  }

/**
 * Foreground for the warning "No features selected" hint shown in a tool's header when the tool
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

/**
 * Per-step availability of one lookup-chain step. UNKNOWN suppresses emphasis entirely (initial
 * render before probes complete). PARTIAL is only meaningful for the SDK step (some project SDKs
 * have the tool, others don't); Path and uvx are binary FOUND / NOT_FOUND.
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
 * Render the fixed `SDK → Path → uvx` lookup chain as informational HTML for a tool's header.
 *
 * The chain is no longer selectable; it always runs SDK first, then $PATH, then uvx. To convey the
 * sequence (ticket concern #2) the steps are joined by arrows. To keep it quiet (ticket concern #1:
 * no per-method status icons / divider) each step is plain text — the **first step that would
 * actually resolve** the tool (FOUND, or PARTIAL for SDK) is emphasized in the normal foreground;
 * every other step is muted. The SDK step also shows a `(matched/total)` count when the project has
 * interpreters. Before probes finish (UNKNOWN) nothing is emphasized.
 */
@Nls
@Suppress("HardCodedStringLiteral")
internal fun lookupChainHtml(
  sdkStatus: ChainStepStatus,
  sdkMatched: Int,
  sdkTotal: Int,
  pathStatus: ChainStepStatus,
  uvxStatus: ChainStepStatus,
): String {
  // Show the "(matched/total)" count only when there is more than one environment — with a single
  // env it is just noise ("(0/1)" / "(1/1)").
  val sdkLabel = if (sdkTotal > 1) {
    PyToolsUiBundle.message("settings.external.tools.chain.env") + " ($sdkMatched/$sdkTotal)"
  }
  else {
    PyToolsUiBundle.message("settings.external.tools.chain.env")
  }
  val steps = listOf(
    sdkLabel to sdkStatus,
    PyToolsUiBundle.message("settings.external.tools.mode.path") to pathStatus,
    PyToolsUiBundle.message("settings.external.tools.mode.uvx") to uvxStatus,
  )
  // Colour by availability: a step that would resolve (FOUND / PARTIAL) reads in the normal
  // foreground; one that can't (NOT_FOUND / not-yet-probed) is muted. Independently, the first
  // resolving step — where the tool actually runs — is bold, to show where the chain stops.
  val activeIndex = steps.indexOfFirst { it.second == ChainStepStatus.FOUND || it.second == ChainStepStatus.PARTIAL }
  val availableHex = "%06x".format(UIUtil.getLabelForeground().rgb and 0xFFFFFF)
  val mutedHex = "%06x".format(UIUtil.getInactiveTextColor().rgb and 0xFFFFFF)
  return buildString {
    append("<html>")
    steps.forEachIndexed { i, (label, status) ->
      if (i > 0) append("&nbsp;&rarr; ")
      val available = status == ChainStepStatus.FOUND || status == ChainStepStatus.PARTIAL
      val hex = if (available) availableHex else mutedHex
      if (i == activeIndex) {
        append("<b><font color='#$hex'>").append(label).append("</font></b>")
      }
      else {
        append("<font color='#$hex'>").append(label).append("</font>")
      }
    }
    append("</html>")
  }
}
