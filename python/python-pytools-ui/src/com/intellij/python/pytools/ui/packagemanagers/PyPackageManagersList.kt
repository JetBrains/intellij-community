// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui.packagemanagers

import com.intellij.ide.ui.search.SearchUtil
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.PackageManagerPyTool
import com.intellij.python.pytools.getCustomExecutablePath
import com.intellij.python.pytools.setCustomExecutablePath
import com.intellij.python.pytools.statistics.PyToolActionSource
import com.intellij.python.pytools.ui.PyToolsUiBundle
import com.intellij.python.pytools.ui.configuration.PyToolManagementController
import com.intellij.python.pytools.ui.configuration.RowState
import com.intellij.python.pytools.ui.configuration.ToolRow
import com.intellij.python.pytools.ui.configuration.browseExecutablePath
import com.intellij.python.pytools.ui.configuration.checkNoPathErrors
import com.intellij.python.pytools.ui.configuration.probeVersion
import com.intellij.ui.ClientProperty
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Scrollable

/** Fixed width of the Tool column, shared by the header caption and every row so they line up. */
internal fun pmToolColumnWidth(): Int = JBUI.scale(180)

/** Callbacks a [PyPackageManagerRowPanel] needs from its owning list. */
internal interface PmHost {
  val project: Project
  fun isUpgradeAvailable(row: ToolRow): Boolean
  /** The version an Upgrade would move [row] to, when known. */
  fun upgradeTargetVersion(row: ToolRow): String?
  fun browsePath(row: ToolRow)
  fun installOnPath(row: ToolRow)
  fun upgradeOnPath(row: ToolRow)
  fun resetPath(row: ToolRow)
}

/**
 * Body of the "Package Managers" page: a flat, scrollable list of [PyPackageManagerRowPanel]s, one
 * per [PackageManagerPyTool]. Reuses the External Tools infrastructure — [PyToolManagementController]
 * (install/upgrade/uv-availability/outdated) and the [ToolRow] path-probe/browse helpers. The custom
 * path is persisted per Eel machine via the shared [com.intellij.python.pytools.getCustomExecutablePath]
 * / [com.intellij.python.pytools.setCustomExecutablePath] — the same mechanism the External Tools page
 * uses.
 */
internal class PyPackageManagersList(
  override val project: Project,
  private val uv: PyToolManagementController,
) : PmHost {

  /** Target for which the page shows and edits custom paths — the project's environment (local, WSL, …). */
  private val eelDescriptor = project.getEelDescriptor()

  private val rows: List<ToolRow> = PyTool.EP_NAME.extensionList
    .filter { it is PackageManagerPyTool }
    .sortedBy { it.presentableName.lowercase() }
    .map { ToolRow(it, RowState(enabled = true, customPath = it.getCustomExecutablePath(eelDescriptor))) }

  private val rowPanels: Map<ToolRow, PyPackageManagerRowPanel> =
    rows.associateWith { PyPackageManagerRowPanel(it, this) }

  val view: JComponent = object : JPanel(VerticalLayout(0)), Scrollable {
    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(16)
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(16)
    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false
  }.apply {
    rows.forEach { add(rowPanels.getValue(it)) }
  }

  private var scope: CoroutineScope? = null
  private var spotlightRow: Int = -1

  // ---------- PmHost ----------

  override fun isUpgradeAvailable(row: ToolRow): Boolean = uv.isUpgradeAvailable(row)
  override fun upgradeTargetVersion(row: ToolRow): String? = uv.latestVersionFor(row)
  override fun browsePath(row: ToolRow) {
    row.browseExecutablePath(project, view) { chosen -> setCustomPath(row, chosen.toString()) }
  }
  override fun installOnPath(row: ToolRow): Unit = uv.installTool(row, PyToolActionSource.SETTINGS_TABLE)
  override fun upgradeOnPath(row: ToolRow): Unit = uv.upgradeTool(row, PyToolActionSource.SETTINGS_TABLE)
  override fun resetPath(row: ToolRow): Unit = setCustomPath(row, "")

  private fun setCustomPath(row: ToolRow, value: String) {
    val trimmed = value.trim()
    row.staged = row.staged.copy(customPath = trimmed.takeIf { it.isNotEmpty() }?.let { Path.of(it) })
    probeRow(row, isCustomEdit = true)
    refreshRow(row)
  }

  // ---------- Lifecycle ----------

  fun onShown(scope: CoroutineScope) {
    this.scope = scope
    rows.forEach { it.lastSuccessMessage = null }
    rows.forEach { probeRow(it) }
  }

  fun isModified(): Boolean = rows.any { it.staged.customPath != it.tool.getCustomExecutablePath(eelDescriptor) }

  fun apply() {
    checkNoPathErrors(rows)
    rows.forEach { row ->
      if (row.staged.customPath != row.tool.getCustomExecutablePath(eelDescriptor)) {
        row.tool.setCustomExecutablePath(eelDescriptor, row.staged.customPath)
      }
    }
    rows.forEach { refreshRow(it) }
  }

  fun reset() {
    rows.forEach { row ->
      row.staged = row.staged.copy(customPath = row.tool.getCustomExecutablePath(eelDescriptor))
      // Re-probe so the path field / version reflect the reverted value, and clear any stale error
      // from a rejected edit (a non-custom probe never clears it on its own).
      row.pathError = null
      probeRow(row)
    }
    rows.forEach { refreshRow(it) }
  }

  fun disposeUIResources() {}

  // ---------- Search ----------

  fun findMatchingRowIndex(needle: String): Int {
    val lowercased = needle.lowercase()
    return rows.indexOfFirst { it.tool.presentableName.lowercase().contains(lowercased) }
  }

  fun selectForSearchHit(row: Int) {
    clearSelection()
    val panel = rowPanels[rows.getOrNull(row) ?: return] ?: return
    spotlightRow = row
    panel.setSpotlight(true)
    panel.scrollRectToVisible(Rectangle(0, 0, panel.width, panel.height))
  }

  fun clearSelection() {
    if (spotlightRow < 0) return
    rowPanels[rows.getOrNull(spotlightRow) ?: return]?.setSpotlight(false)
    spotlightRow = -1
  }

  // ---------- Probe / refresh ----------

  private fun probeRow(row: ToolRow, isCustomEdit: Boolean = false) {
    val scope = scope ?: return
    row.probeVersion(scope, project, isCustomEdit, onUpdated = ::refreshRow)
  }

  fun refreshRow(row: ToolRow) {
    rowPanels[row]?.refresh()
  }

  fun fireAllRowsChanged() {
    rows.forEach { refreshRow(it) }
  }
}

/** The static "Tool" / "Path" caption strip installed as the list's scroll-pane column header. */
internal fun buildPackageManagersHeaderBar(): JComponent {
  return object : JPanel(BorderLayout()), Scrollable {
    override fun getPreferredSize(): Dimension = Dimension(super.getPreferredSize().width, JBUI.scale(24))
    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(16)
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(16)
    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false
  }.apply {
    isOpaque = true
    background = UIUtil.getPanelBackground()
    border = JBUI.Borders.compound(JBUI.Borders.customLineBottom(JBColor.border()), JBUI.Borders.empty(0, 8))
    ClientProperty.put(this, SearchUtil.SEARCH_SKIP_COMPONENT_KEY, true)
    add(pmHeaderText(PyToolsUiBundle.message("settings.external.tools.column.name")).let {
      com.intellij.python.pytools.ui.configuration.fixedWidthPanel(pmToolColumnWidth(), it)
    }, BorderLayout.WEST)
    add(pmHeaderText(PyToolsUiBundle.message("settings.package.managers.column.path")), BorderLayout.CENTER)
  }
}

/** Paint-only caption; not a JLabel so the Settings search spotlight can't match column titles. */
private fun pmHeaderText(text: String): JComponent = object : JComponent() {
  init {
    font = UIUtil.getLabelFont()
  }

  override fun getPreferredSize(): Dimension {
    val fm = getFontMetrics(font)
    return Dimension(fm.stringWidth(text), fm.height)
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val g2 = g.create() as Graphics2D
    try {
      GraphicsUtil.applyRenderingHints(g2)
      g2.font = font
      g2.color = UIUtil.getLabelForeground()
      val fm = g2.fontMetrics
      g2.drawString(text, 0, (height + fm.ascent - fm.descent) / 2)
    }
    finally {
      g2.dispose()
    }
  }
}
