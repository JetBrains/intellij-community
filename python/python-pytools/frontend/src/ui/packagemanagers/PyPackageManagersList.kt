// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.frontend.ui.packagemanagers

import com.intellij.ide.ui.search.SearchUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.project.projectId
import com.intellij.python.pytools.common.PyToolApi
import com.intellij.python.pytools.common.PyToolRequest
import com.intellij.python.pytools.common.PyToolSetPathRequest
import com.intellij.python.pytools.common.PyToolsRequest
import com.intellij.python.pytools.frontend.PyToolFrontend as PyTool
import com.intellij.python.pytools.frontend.PackageManagerPyToolFrontend as PackageManagerPyTool
import com.intellij.python.pytools.common.PyToolActionSource
import com.intellij.python.pytools.frontend.ui.PyToolsUiBundle
import com.intellij.python.pytools.frontend.ui.configuration.PyToolManagementController
import com.intellij.python.pytools.frontend.ui.configuration.PathActionHost
import com.intellij.python.pytools.frontend.ui.configuration.RowState
import com.intellij.python.pytools.frontend.ui.configuration.ToolRow
import com.intellij.python.pytools.frontend.ui.configuration.browseExecutablePath
import com.intellij.python.pytools.frontend.ui.configuration.applyBackendPath
import com.intellij.python.pytools.frontend.ui.configuration.applyBackendState
import com.intellij.python.pytools.frontend.ui.configuration.checkNoPathErrors
import com.intellij.python.pytools.frontend.ui.configuration.fixedWidthPanel
import com.intellij.python.pytools.frontend.ui.configuration.headerText
import com.intellij.python.pytools.frontend.ui.configuration.loadVersion
import com.intellij.python.pytools.frontend.ui.configuration.probeVersion
import com.intellij.ui.ClientProperty
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Scrollable

/**
 * Fixed width of the Tool column, shared by the header caption and every row so they line up.
 * The column holds an icon, a gap, and a bold manager name. Today's longest name leaves room to
 * spare at this width, and the Path column starts closer to the names.
 */
internal fun pmToolColumnWidth(): Int = JBUI.scale(120)

/** Callbacks a [PyPackageManagerRowPanel] needs from its owning list. */
internal interface PmHost : PathActionHost {
  val project: Project
  /** The version an Upgrade would move [row] to, when known. */
  fun browsePath(row: ToolRow)
}

/**
 * Body of the "Package Managers" page: a flat, scrollable list of [PyPackageManagerRowPanel]s, one
 * per [PackageManagerPyTool]. Reuses the External Tools infrastructure — [PyToolManagementController]
 * (install/upgrade/uv-availability/outdated) and the [ToolRow] path-probe/browse helpers. The custom
 * path is persisted by the backend through [PyToolApi]. The External Tools page uses the same API.
 */
internal class PyPackageManagersList(
  override val project: Project,
  private val uv: PyToolManagementController,
) : PmHost {

  private val rows: List<ToolRow> = PyTool.extensionList
    .filterIsInstance<PackageManagerPyTool>()
    .sortedBy { it.presentableName.lowercase() }
    .map { ToolRow(it, RowState(enabled = true, customPath = null)) }

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
  override fun upgradeTargetVersion(row: ToolRow): Version? = uv.latestVersionFor(row)
  override fun browsePath(row: ToolRow) {
    browseExecutablePath(project, view) { chosen -> setCustomPath(row, chosen) }
  }
  override fun installOnPath(row: ToolRow): Unit = uv.installTool(row, PyToolActionSource.SETTINGS_TABLE)
  override fun upgradeOnPath(row: ToolRow): Unit = uv.upgradeTool(row, PyToolActionSource.SETTINGS_TABLE)
  override fun resetPath(row: ToolRow): Unit = setCustomPath(row, "")

  private fun setCustomPath(row: ToolRow, value: String) {
    val trimmed = value.trim()
    row.staged = row.staged.copy(customPath = trimmed.takeIf { it.isNotEmpty() })
    probeRow(row, isCustomEdit = true)
    refreshRow(row)
  }

  // ---------- Lifecycle ----------

  fun onShown(scope: CoroutineScope) {
    this.scope = scope
    rows.forEach { it.lastSuccessMessage = null }
    // Per row, and the path before the state: a row shows its own path as soon as the backend resolves
    // it, and no row waits for the slowest one. The path is cached on the backend, while a state also
    // needs the tool listing and, for a path the listing does not cover, a `--version` run.
    rows.forEach { row ->
      scope.launch {
        val path = PyToolApi.getInstance().getPaths(
          PyToolsRequest(project.projectId(), listOf(row.tool.toolId)),
        ).singleOrNull() ?: return@launch
        if (row.applyBackendPath(path)) refreshRow(row)
      }
      scope.launch {
        val state = PyToolApi.getInstance().getStates(
          PyToolsRequest(project.projectId(), listOf(row.tool.toolId)),
        ).singleOrNull()
        state?.let { row.applyBackendState(it, updateStagedPath = true) }
        refreshRow(row)
        // This page shows every row's version, so every row asks for it.
        row.loadVersion(this, project, ::refreshRow)
      }
    }
  }

  fun isModified(): Boolean = rows.any { it.staged.customPath != it.persistedCustomPath }

  fun apply() {
    checkNoPathErrors(rows)
    rows.forEach { row ->
      if (row.staged.customPath != row.persistedCustomPath) {
        val state = runWithModalProgressBlocking(
          project,
          PyToolsUiBundle.message("settings.external.tools.apply.progress"),
        ) {
          PyToolApi.getInstance().setPath(
            PyToolSetPathRequest(PyToolRequest(project.projectId(), row.tool.toolId), row.staged.customPath),
          )
        }
        row.applyBackendState(state)
      }
    }
    rows.forEach { refreshRow(it) }
  }

  fun reset() {
    rows.forEach { row ->
      row.staged = row.staged.copy(customPath = row.persistedCustomPath)
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
    row.probeVersion(scope, project, isCustomEdit) { updated ->
      refreshRow(updated)
      // A state that resolved another path dropped the version with it, so ask for the new one. A no-op
      // when the row already holds the version of the path it now shows.
      updated.loadVersion(scope, project, ::refreshRow)
    }
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
    add(headerText(PyToolsUiBundle.message("settings.external.tools.column.name")).let {
      fixedWidthPanel(pmToolColumnWidth(), it)
    }, BorderLayout.WEST)
    add(headerText(PyToolsUiBundle.message("settings.package.managers.column.path")), BorderLayout.CENTER)
  }
}
