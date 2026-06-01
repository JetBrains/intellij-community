// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui.configuration

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.text.StringUtil
import com.intellij.python.pytools.Version
import com.intellij.python.pytools.ui.PyToolsUiBundle
import com.intellij.python.pytools.ui.icons.PythonPytoolsUIIcons
import com.intellij.util.ui.JBUI
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JTable

/**
 * Combined host surface used by the position-aware tooltip orchestrators: they need both the
 * project (for `summaryFor(project)`) and the icon-kind resolver (for the Path column). The
 * configurable implements both [ToolCellHost] and [PathCellHost], so it satisfies this without
 * extra plumbing.
 */
internal interface TooltipHost : ToolCellHost, PathCellHost, ModeCellHost

/**
 * Top-level dispatcher invoked from the table's `getToolTipText(MouseEvent)` override. Picks
 * the right column-specific orchestrator based on the pointer location, or falls back to
 * [default] (JTable's own renderer-driven tooltip) when the event falls outside the
 * recognized columns. The hit-test geometry uses the live cell rect rather than stale renderer
 * state, which is the whole reason the configurable bypasses JTable's default forwarding.
 */
internal fun resolveCellTooltip(
  event: MouseEvent,
  table: JTable,
  rows: List<ToolRow>,
  host: TooltipHost,
  default: () -> String?,
): String? {
  val viewRow = table.rowAtPoint(event.point)
  val viewCol = table.columnAtPoint(event.point)
  if (viewRow < 0 || viewCol < 0) return default()
  val toolRow = rows.getOrNull(viewRow) ?: return default()
  val cellRect = table.getCellRect(viewRow, viewCol, false)
  return when (viewCol) {
    COL_TOOL -> toolColumnTooltip(toolRow, host, event.x, cellRect)
    COL_MODE -> modeColumnTooltip(toolRow)
    COL_PATH -> pathColumnTooltip(toolRow, host, event.x, cellRect)
    else -> default()
  }
}

/**
 * Tooltip for the Lookup-column cell: a short description of the fallback strategy implied by
 * the picked mode, followed by the per-SDK detection list (when probed) so the user can see
 * exactly which project SDKs have the tool's binary and at which path.
 */
private fun modeColumnTooltip(toolRow: ToolRow): String = buildString {
  append("<html>")
  // Prefix with the tool name so each row's tooltip text differs even when the strategy and
  // per-SDK detection happen to be identical — JTable / ToolTipManager only refreshes the
  // visible tooltip when the returned string actually changes, otherwise the previous one
  // sticks as the pointer crosses into a new row.
  append("<b>")
  append(StringUtil.escapeXmlEntities(toolRow.tool.presentableName))
  append("</b><br>")
  append(StringUtil.escapeXmlEntities(lookupStrategyText(toolRow.staged.mode)))
  // Surface the per-SDK detection list only when `Sdk` is actually part of the active fallback
  // chain (i.e. the user picked `Sdk` as the starting mode). Picking `Path` or `uvx` skips the
  // SDK step entirely, so the list would be informational noise.
  val avail = toolRow.sdkAvailability
  val sdkInChain = toolRow.staged.mode == com.intellij.python.pytools.configuration.ExecutableDiscoveryMode.INTERPRETER
  if (sdkInChain && avail != null && avail.entries.isNotEmpty()) {
    append("<br><br>")
    append(StringUtil.escapeXmlEntities(PyToolsUiBundle.message("settings.external.tools.lookup.sdk.tooltip.header")))
    avail.entries.forEach { entry ->
      append("<br>&nbsp;&nbsp;")
      append(StringUtil.escapeXmlEntities(entry.sdkLabel))
      append(": ")
      val path = entry.binaryPath
      if (path != null) {
        append(StringUtil.escapeXmlEntities(path.toString()))
      }
      else {
        append("<span style='color:gray'>")
        append(StringUtil.escapeXmlEntities(PyToolsUiBundle.message("settings.external.tools.lookup.sdk.tooltip.not.installed")))
        append("</span>")
      }
    }
  }
  append("</html>")
}

/** Human-readable description of the fallback chain implied by the picked [mode]. */
private fun lookupStrategyText(mode: com.intellij.python.pytools.configuration.ExecutableDiscoveryMode): String = when (mode) {
  com.intellij.python.pytools.configuration.ExecutableDiscoveryMode.INTERPRETER ->
    PyToolsUiBundle.message("settings.external.tools.lookup.strategy.sdk")
  com.intellij.python.pytools.configuration.ExecutableDiscoveryMode.PATH ->
    PyToolsUiBundle.message("settings.external.tools.lookup.strategy.path")
  com.intellij.python.pytools.configuration.ExecutableDiscoveryMode.UVX ->
    PyToolsUiBundle.message("settings.external.tools.lookup.strategy.uvx")
}

/**
 * Tooltip for a Tool-column cell: the action hint when the pointer is over the gear icon
 * (and the tool has something to configure), otherwise the full tool name + options summary.
 */
private fun toolColumnTooltip(toolRow: ToolRow, host: TooltipHost, eventX: Int, cellRect: Rectangle): String {
  val onGear = isOverIcon(eventX, cellRect, PythonPytoolsUIIcons.Settings.iconWidth)
  if (onGear && toolRow.staged.enabled && toolRow.tool.detailConfigurable != null) {
    return PyToolsUiBundle.message("settings.external.tools.edit.tooltip", toolRow.tool.presentableName)
  }
  // Match the cell-rendering rule: a disabled tool's options aren't surfaced anywhere — its
  // bracketed summary is hidden in the cell, so the tooltip should not leak it either.
  val options = if (toolRow.staged.enabled) {
    toolRow.tool.summaryFor(host.project).takeIf { it.isNotBlank() }
  }
  else null
  return buildToolTooltip(toolRow, options)
}

/**
 * Tooltip for a Path-column cell: the browse hint when the pointer is over the rightmost
 * OpenDisk icon, an install / upgrade hint when over the action icon to its left, or the full
 * path + version + error otherwise.
 */
private fun pathColumnTooltip(toolRow: ToolRow, host: TooltipHost, eventX: Int, cellRect: Rectangle): String? {
  val pathFieldValue = toolRow.pathFieldValue
  val rightEdge = cellRect.x + cellRect.width - JBUI.scale(5)
  if (toolRow.staged.enabled) {
    // Browse occupies the rightmost slot.
    val browseLeft = rightEdge - AllIcons.General.OpenDisk.iconWidth
    if (eventX in browseLeft..rightEdge) {
      return PyToolsUiBundle.message("settings.external.tools.path.edit.tooltip")
    }
    // The slot to the left of browse holds one of: the regular install / upgrade / reset
    // action icon, or — when the row has [ToolRow.lastSuccessMessage] from a recent action —
    // a ✓. The success message wins so hovering the ✓ tells the user exactly what happened.
    val successMessage = toolRow.lastSuccessMessage
    val iconKind = if (successMessage == null) host.iconKindFor(toolRow, pathFieldValue) else PathIconKind.NONE
    val actionIcon = if (successMessage != null) AllIcons.Actions.Checked else iconKind.icon
    if (actionIcon != null) {
      val actionRight = browseLeft - JBUI.scale(4)
      val actionLeft = actionRight - actionIcon.iconWidth
      if (eventX in actionLeft..actionRight) {
        val hint = successMessage ?: actionHintFor(iconKind, host.latestVersionFor(toolRow))
        if (hint != null) return hint
      }
    }
  }

  val rawPath = when (pathFieldValue) {
    is PathFieldValue.Custom -> pathFieldValue.path.toString()
    is PathFieldValue.AutoDetected -> pathFieldValue.path.toString()
    PathFieldValue.NotFound, null -> null
  }

  return rawPath?.let {
    buildPathTooltip(it, toolRow.version, toolRow.pathError, toolRow.belowMinVersionMessage)
  }
}

/**
 * HTML tooltip shown when the pointer is over the Tool cell text (not the gear icon).
 * Surfaces the full tool name and options summary so the user can read them when the
 * cell text is truncated, plus the tool's one-line description.
 */
private fun buildToolTooltip(toolRow: ToolRow, options: String?): String = buildString {
  append("<html>")
  append(StringUtil.escapeXmlEntities(toolRow.tool.presentableName))
  append("<br>")
  append(StringUtil.escapeXmlEntities(toolRow.tool.description))
  if (!options.isNullOrBlank()) {
    append("<br><span style='color:gray'>[")
    append(StringUtil.escapeXmlEntities(options))
    append("]</span>")
  }
  append("</html>")
}

/**
 * HTML tooltip shown when the pointer is over the Path cell text (not the hover icon).
 * Always shows the full path so the user can read it when the cell is truncated, plus
 * the version and any validation error.
 */
private fun buildPathTooltip(
  rawPath: String,
  version: Version?,
  pathError: String?,
  belowMinMessage: String? = null,
): String = buildString {
  append("<html>")
  val pathDisplay = rawPath.ifEmpty { PyToolsUiBundle.message("settings.external.tools.path.not.found") }
  append(StringUtil.escapeXmlEntities(pathDisplay))
  if (version != null) {
    append("<br>")
    append(StringUtil.escapeXmlEntities(
      PyToolsUiBundle.message("settings.external.tools.path.version.tooltip", version)
    ))
  }
  if (belowMinMessage != null) {
    append("<br><span style='color:#a06000'>")
    append(StringUtil.escapeXmlEntities(belowMinMessage))
    append("</span>")
  }
  if (pathError != null) {
    append("<br><span style='color:red'>")
    append(StringUtil.escapeXmlEntities(pathError))
    append("</span>")
  }
  append("</html>")
}

/**
 * Action hint shown when the pointer is over the Path column's hover action icon. The upgrade
 * hint has two wordings: when modern uv reports a [latestVersion] for the tool we surface it
 * ("Upgrade to X.Y.Z"); when we have no way to know in advance (legacy uv before `--outdated`)
 * we fall back to the hedged "Try to upgrade".
 */
private fun actionHintFor(kind: PathIconKind, latestVersion: String?): String? = when (kind) {
  PathIconKind.NONE -> null
  PathIconKind.INSTALL -> PyToolsUiBundle.message("settings.external.tools.install.via.uv.tooltip")
  PathIconKind.RESET -> PyToolsUiBundle.message("settings.external.tools.path.reset.tooltip")
  PathIconKind.UPGRADE -> if (latestVersion != null) {
    PyToolsUiBundle.message("settings.external.tools.path.upgrade.to.version.tooltip", latestVersion)
  }
  else {
    // Legacy uv: we can't promise the click will do anything, so hedge the wording.
    PyToolsUiBundle.message("settings.external.tools.path.upgrade.unknown.tooltip")
  }
}

/** True if [eventX] (in table coordinates) lies within the right-edge icon hit-zone of [cellRect]. */
internal fun isOverIcon(eventX: Int, cellRect: Rectangle, iconWidth: Int): Boolean {
  val iconRight = cellRect.x + cellRect.width - JBUI.scale(5)
  val iconLeft = iconRight - iconWidth
  return eventX in iconLeft..iconRight
}
