// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.frontend.ui.configuration

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.project.projectId
import com.intellij.python.pytools.common.ProjectLevelPyToolApi
import com.intellij.python.pytools.common.PyToolApi
import com.intellij.python.pytools.common.PyToolSdkDto
import com.intellij.python.pytools.common.PyToolSetEnabledRequest
import com.intellij.python.pytools.common.PyToolSetPathRequest
import com.intellij.python.pytools.common.PyToolRequest
import com.intellij.python.pytools.common.PyToolsRequest
import com.intellij.python.pytools.frontend.PyToolFrontend as PyTool
import com.intellij.python.pytools.frontend.PyToolsFrontendState
import com.intellij.python.pytools.frontend.ProjectLevelPyToolFrontend
import com.intellij.python.pytools.common.PyToolActionSource
import com.intellij.python.pytools.common.PyToolEventKind
import com.intellij.python.pytools.common.PyToolEnabledStateDto
import com.intellij.python.pytools.common.FusId
import com.intellij.python.pytools.common.PyToolLogEventRequest
import com.intellij.python.pytools.frontend.ui.PyToolsUiBundle
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Scrollable

/**
 * Callbacks a [PyExternalToolRowPanel] needs from its owning [PyExternalToolsList]: the informational
 * lookup-chain text, path/SDK actions, and the "is an upgrade available" predicate. Keeps the row
 * component free of any direct reference to the list or the uv controller.
 */
internal interface RowHost : PathActionHost {
  val project: Project
  fun lookupChainHtml(row: ToolRow): @Nls String
  /** Explanation for the lookup chain when an enabled tool resolves nowhere (red chain); `null` otherwise. */
  fun lookupChainTooltip(row: ToolRow): @Nls String?
  /**
   * The user just expanded [row]. The host collapses every other row, then makes sure the background
   * state (the path and version probe) is fresh.
   */
  fun onRowExpanded(row: ToolRow)
  fun browsePath(row: ToolRow)
  fun installIntoSdk(row: ToolRow, sdk: PyToolSdkDto, anchor: JComponent)
}

/**
 * The External Tools page body: a scrollable vertical stack of [PyExternalToolRowPanel]s (one per
 * [ProjectLevelPyToolFrontend]) that replaces the former table. Owns the row list, the probe orchestration, and
 * the page lifecycle hooks ([onShown] / [isModified] / [apply] / [reset] / [disposeUIResources])
 * that the configurable delegates to, plus the settings-search select/scroll behaviour.
 *
 * The configurable supplies [project] and the [uv] controller (install / upgrade / uv availability).
 * The probe-launching scope arrives lazily via [onShown], so probes live for the panel's
 * showing-lifetime and are cancelled automatically when the page is hidden.
 */
internal class PyExternalToolsList(
  override val project: Project,
  private val uv: PyToolManagementController,
) : RowHost {

  private val persistedPaths = mutableMapOf<FusId, String?>()

  private val enabledStates = PyToolsFrontendState.getInstance(project)

  /**
   * Source-of-truth row list, materialised once from the [PyTool] extension point.
   *
   * The enable flag is seeded from [PyToolsFrontendState], a synchronous mirror of the backend state, so a
   * row paints its real toggle on the first frame. A hardcoded `false` showed every enabled tool as off
   * until the seconds-long [loadState] answered. The seed goes into the staged state *and* the persisted
   * baseline, so a seeded row is not modified and Apply sends no `setEnabled` for it.
   */
  private val rows: List<ToolRow> = PyTool.extensionList
    .filterIsInstance<ProjectLevelPyToolFrontend<*>>()
    .sortedBy { it.presentableName.lowercase() }
    .map { tool ->
      val enabled = enabledStates.isEnabled(tool.fusId)
      ToolRow(tool, RowState(enabled = enabled, customPath = null), persistedEnabled = enabled)
    }

  private val rowPanels: Map<ToolRow, PyExternalToolRowPanel> =
    rows.associateWith { PyExternalToolRowPanel(it, this) }

  /**
   * Vertical container; each row fills the width and keeps its own preferred height. Tracks the
   * viewport width (via [Scrollable]) so the rows' right-anchored Lookup column lines up with the
   * header bar's, and scrolls vertically for the height.
   */
  val view: JComponent = object : JPanel(VerticalLayout(0)), Scrollable {
    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(16)
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(16)
    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false
  }.apply {
    rows.forEach { add(rowPanels.getValue(it)) }
  }

  /** The active showing-scope, set by [onShown]; `null` before the first show. */
  private var scope: CoroutineScope? = null

  /** Row currently highlighted by a settings-search hit; -1 when none. */
  private var spotlightRow: Int = -1

  // ---------- RowHost ----------

  override fun lookupChainHtml(row: ToolRow): String {
    val sdk = row.sdkAvailability
    return lookupChainHtml(
      sdkStatus = sdk.toChainStatus(),
      sdkMatched = sdk?.matchedCount ?: 0,
      sdkTotal = sdk?.totalCount ?: 0,
      pathStatus = row.pathFieldValue.toChainStatus(),
      uvxStatus = uvxChainStatus(uv.uvAvailable.get()),
      unresolved = row.staged.enabled && row.resolvesNowhere(uv.uvAvailable.get()),
    )
  }

  override fun lookupChainTooltip(row: ToolRow): String? =
    if (row.staged.enabled && row.resolvesNowhere(uv.uvAvailable.get()))
      PyToolsUiBundle.message("settings.external.tools.unresolved.chain.tooltip")
    else null

  override fun isUpgradeAvailable(row: ToolRow): Boolean = uv.isUpgradeAvailable(row)
  override fun upgradeTargetVersion(row: ToolRow): Version? = uv.latestVersionFor(row)

  override fun onRowExpanded(row: ToolRow) {
    // Keep one row open at a time. Many open rows make the page hard to read.
    rowPanels.forEach { (other, panel) -> if (other !== row) panel.collapse() }
    probeRow(row)
    // The expanded body is the only place this page shows a version, so this is where it is asked for.
    scope?.let { row.loadVersion(it, project, ::refreshRow) }
  }

  override fun browsePath(row: ToolRow) {
    browseExecutablePath(project, view) { chosen -> setCustomPath(row, chosen) }
  }

  override fun installOnPath(row: ToolRow): Unit = uv.installTool(row, PyToolActionSource.SETTINGS_TABLE)

  override fun upgradeOnPath(row: ToolRow): Unit = uv.upgradeTool(row, PyToolActionSource.SETTINGS_TABLE)

  override fun resetPath(row: ToolRow): Unit = setCustomPath(row, "")

  override fun installIntoSdk(row: ToolRow, sdk: PyToolSdkDto, anchor: JComponent): Unit =
    uv.installIntoSdkChoosingGroup(row, sdk, anchor, PyToolActionSource.SETTINGS_TABLE)

  /** Route a chosen/cleared custom path through the standard re-probe + refresh flow. */
  private fun setCustomPath(row: ToolRow, value: String) {
    val trimmed = value.trim()
    row.staged = row.staged.copy(customPath = trimmed.takeIf { it.isNotEmpty() })
    probeRow(row, isCustomEdit = true)
    refreshRow(row)
  }

  // ---------- Lifecycle (delegated from the configurable) ----------

  fun onShown(scope: CoroutineScope) {
    this.scope = scope
    // Clear any leftover ✓ from a previous settings session — keeping it would mislead the user
    // about whether the underlying tool state is still up to date.
    rows.forEach { it.lastSuccessMessage = null }
    // Per row, and the path before the state: a row shows its own path as soon as the backend resolves
    // it, and no row waits for the slowest one. The path is cached on the backend, while a state also
    // needs the tool listing and, for a path the listing does not cover, a `--version` run.
    rows.forEach { row ->
      scope.launch { loadPath(row) }
      scope.launch { loadConfiguration(row) }
      scope.launch { loadState(row) }
      scope.launch { loadConfiguration(row) }
    }
    scope.launch { probeAllSdks() }
  }

  private suspend fun loadPath(row: ToolRow) {
    val path = PyToolApi.getInstance().getPaths(
      PyToolsRequest(project.projectId(), listOf(row.tool.fusId)),
    ).singleOrNull() ?: return
    if (!row.applyBackendPath(path)) return
    // Keep the modified/apply baseline in step with the staged path this just set.
    persistedPaths[row.tool.fusId] = row.persistedCustomPath
    refreshRow(row)
  }

  /**
   * Fill the row's feature configuration, which the header summary reads.
   *
   * Asked for on its own because it is cheap — a settings read on the backend — while a full state also
   * needs the tool listing and the executable detection. Without it an enabled tool's features stayed
   * unnamed, and the header claimed none were selected, for as long as the state took.
   */
  private suspend fun loadConfiguration(row: ToolRow) {
    // A tool that reports no configuration leaves the row uninformed on purpose: the state settles it, and
    // until then the header says nothing rather than claiming the tool has no features selected.
    row.configuration = ProjectLevelPyToolApi.getInstance().getConfiguration(
      PyToolRequest(project.projectId(), row.tool.fusId),
    ) ?: return
    row.configurationLoaded = true
    refreshRow(row)
  }

  private suspend fun loadState(row: ToolRow) {
    val state = PyToolApi.getInstance().getStates(
      PyToolsRequest(project.projectId(), listOf(row.tool.fusId)),
    ).singleOrNull()
    if (state != null) {
      row.applyBackendState(state, updateStagedPath = true, updateStagedEnabled = true)
      persistedPaths[row.tool.fusId] = row.persistedCustomPath
    }
    refreshRow(row)
  }

  /**
   * Single read-action snapshot of the project's Python SDKs, reused to compute [SdkAvailability]
   * for every row (avoids each tool re-touching the project model).
   */
  private suspend fun probeAllSdks() {
    for (row in rows) {
      val entries = PyToolApi.getInstance().getSdkStates(PyToolRequest(project.projectId(), row.tool.fusId))
      row.sdkAvailability = SdkAvailability(entries)
      refreshRow(row)
    }
  }

  /** True iff any row has unsaved edits — a staged enable/path diff, or a dirty detail configurable. */
  fun isModified(): Boolean = rows.any { row ->
    row.staged != snapshotOf(row) || row.detail?.isModified() == true
  }

  /** Persist all rows' staged state on the backend and apply any dirty detail configurables. */
  fun apply() {
    checkNoPathErrors(rows)
    rows.forEach { row ->
      val detailModified = row.detail?.isModified() == true
      val current = snapshotOf(row)
      val rowChanged = row.staged != current || detailModified
      val pathChanged = row.staged.customPath != current.customPath
      val enabledChanged = row.staged.enabled != current.enabled
      // The path goes before an enable, so the server that the enable starts resolves the binary the
      // user chose. It goes after a disable, so a tool the user turns off does not restart for the path.
      if (enabledChanged && !row.staged.enabled) applyEnabled(row)
      if (pathChanged) applyPath(row)
      if (enabledChanged && row.staged.enabled) applyEnabled(row)
      if (detailModified) {
        try {
          row.detail?.apply()
        }
        catch (e: ConfigurationException) {
          row.detail?.disposeUIResources()
          row.detail = null
          throw e
        }
      }
      if (rowChanged) {
        runWithModalProgressBlocking(project, PyToolsUiBundle.message("settings.external.tools.apply.progress")) {
          PyToolApi.getInstance().logEvent(
            PyToolLogEventRequest(
              PyToolRequest(project.projectId(), row.tool.fusId),
              PyToolActionSource.SETTINGS_TABLE,
              PyToolEventKind.CONFIGURATION_CHANGED,
            ),
          )
        }
      }
    }
    rows.forEach { refreshRow(it) }
  }

  private fun applyPath(row: ToolRow) {
    val backendState = runWithModalProgressBlocking(
      project,
      PyToolsUiBundle.message("settings.external.tools.apply.progress"),
    ) {
      PyToolApi.getInstance().setPath(
        PyToolSetPathRequest(PyToolRequest(project.projectId(), row.tool.fusId), row.staged.customPath),
      )
    }
    row.applyBackendState(backendState)
    persistedPaths[row.tool.fusId] = row.persistedCustomPath
  }

  private fun applyEnabled(row: ToolRow) {
    val backendState = runWithModalProgressBlocking(
      project,
      PyToolsUiBundle.message("settings.external.tools.apply.progress"),
    ) {
      ProjectLevelPyToolApi.getInstance().setEnabled(
        PyToolSetEnabledRequest(PyToolRequest(project.projectId(), row.tool.fusId), row.staged.enabled),
      )
    }
    row.applyBackendState(backendState)
    enabledStates.apply(PyToolEnabledStateDto(backendState.fusId, backendState.enabled))
  }

  /** Revert all rows' staged state to the persisted snapshot and reset any open detail configurables. */
  fun reset() {
    rows.forEach { row ->
      row.staged = snapshotOf(row)
      row.detail?.reset()
      // Re-probe so the path field / version reflect the reverted path, and clear any stale error
      // from a rejected custom edit (a non-custom probe never clears it on its own).
      row.pathError = null
      probeRow(row)
    }
    rows.forEach { refreshRow(it) }
  }

  fun disposeUIResources() {
    rows.forEach { it.detail?.disposeUIResources(); it.detail = null }
  }

  // ---------- Search ----------

  fun findMatchingRowIndex(needle: String): Int {
    val lowercased = needle.lowercase()
    return rows.indexOfFirst { it.tool.presentableName.lowercase().contains(lowercased) }
  }

  /** Highlight [row] with a spotlight border and scroll it into view. */
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

  // ---------- Probe orchestration ----------

  private fun probeRow(row: ToolRow, isCustomEdit: Boolean = false) {
    val scope = scope ?: return
    row.probeVersion(scope, project, isCustomEdit) { updated ->
      refreshRow(updated)
      // A state that resolved another path dropped the version with it, so ask for the new one. A no-op
      // when the row already holds the version of the path it now shows.
      updated.loadVersion(scope, project, ::refreshRow)
    }
  }

  /** Re-render a single row's dynamic content (path, SDK list, chain, summary, action state). */
  fun refreshRow(row: ToolRow) {
    rowPanels[row]?.refresh()
  }

  /** Re-render every row; invoked from the configurable's uv-state-changed handler. */
  fun fireAllRowsChanged() {
    rows.forEach { refreshRow(it) }
  }

  // ---------- Snapshot helper ----------

  private fun snapshotOf(row: ToolRow): RowState {
    return RowState(
      enabled = row.persistedEnabled,
      customPath = persistedPaths[row.tool.fusId],
    )
  }
}
