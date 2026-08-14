// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui.configuration

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.PyToolsState
import com.intellij.python.pytools.ExternalPyTool
import com.intellij.python.pytools.getCustomExecutablePath
import com.intellij.python.pytools.setCustomExecutablePath
import com.intellij.python.pytools.statistics.PyToolActionSource
import com.intellij.python.pytools.statistics.PyToolUsagesCollector
import com.intellij.python.pytools.ui.PyToolTypeEnginePreview
import com.intellij.python.pytools.ui.PyToolsUiBundle
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.awt.Rectangle
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Scrollable

/**
 * Callbacks a [PyExternalToolRowPanel] needs from its owning [PyExternalToolsList]: the informational
 * lookup-chain text, path/SDK actions, and the "is an upgrade available" predicate. Keeps the row
 * component free of any direct reference to the list or the uv controller.
 */
internal interface RowHost {
  val project: Project
  fun lookupChainHtml(row: ToolRow): @Nls String
  /** Explanation for the lookup chain when an enabled tool resolves nowhere (red chain); `null` otherwise. */
  fun lookupChainTooltip(row: ToolRow): @Nls String?
  /** Whether [row]'s tool is the project's current type engine — the **staged** selection if a Type Engine page is open, else the persisted one. */
  fun isTypeEngine(row: ToolRow): Boolean
  /** Called after the user turns off a tool that is the type engine: clears the staged engine so the two stay consistent. */
  fun onTypeEngineToolDisabled()
  fun isUpgradeAvailable(row: ToolRow): Boolean
  /** The version an Upgrade would move [row] to, when known. */
  fun upgradeTargetVersion(row: ToolRow): String?
  /** Ensure background state (path/version probe) is fresh for a row the user just expanded. */
  fun onRowExpanded(row: ToolRow)
  fun browsePath(row: ToolRow)
  fun installOnPath(row: ToolRow)
  fun upgradeOnPath(row: ToolRow)
  fun resetPath(row: ToolRow)
  fun installIntoSdk(row: ToolRow, sdk: Sdk, anchor: JComponent)
}

/**
 * The External Tools page body: a scrollable vertical stack of [PyExternalToolRowPanel]s (one per
 * [ExternalPyTool]) that replaces the former table. Owns the row list, the probe orchestration, and
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

  /** Target whose per-machine custom paths this page shows/edits (the project's environment). */
  private val eelDescriptor = project.getEelDescriptor()

  /** Source-of-truth row list, materialised once from the [PyTool] extension point. */
  private val rows: List<ToolRow> = PyTool.EP_NAME.extensionList
    .filter { it is ExternalPyTool }
    .sortedBy { it.presentableName.lowercase() }
    .map { ToolRow(it, stagedFor(it)) }

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

  /** Scopes the staged-engine observer to this page's lifetime; disposed in [disposeUIResources]. */
  private val engineObserverDisposable = Disposer.newDisposable()

  /** Previous staged engine package, to detect when a tool stops being the staged engine. */
  private var lastStagedEngine: String? = null

  init {
    val preview = PyToolTypeEnginePreview.getInstance(project)
    lastStagedEngine = preview.stagedEnginePackage.get()
    // Live reflection of the staged engine on the tools' toggles.
    preview.stagedEnginePackage.afterChange(engineObserverDisposable) { staged ->
      rows.forEach { row ->
        val pkg = row.tool.packageName.name
        when {
          // Became the staged engine → turn its toggle on.
          staged == pkg && !row.staged.enabled -> {
            row.staged = row.staged.copy(enabled = true); refreshRow(row)
          }
          // Was on only because it was the staged engine (not persisted-enabled) and no longer is →
          // revert to off, so staging an engine and switching away leaves the tool unchanged.
          lastStagedEngine == pkg && staged != pkg && row.staged.enabled && !snapshotOf(row.tool).enabled -> {
            row.staged = row.staged.copy(enabled = false); refreshRow(row)
          }
        }
      }
      lastStagedEngine = staged
    }
    // When the user, on the Type Engine page, chose to turn an engine's tool off while switching away,
    // flip that tool's toggle off here too.
    preview.pendingDisable.afterChange(engineObserverDisposable) { pending ->
      rows.forEach { row ->
        if (row.tool.packageName.name in pending && row.staged.enabled) {
          row.staged = row.staged.copy(enabled = false); refreshRow(row)
        }
      }
    }
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

  override fun isTypeEngine(row: ToolRow): Boolean = isEngineFor(row.tool)

  override fun onTypeEngineToolDisabled() {
    // Deselect the engine in the staging (built-in, "") — not `null`, which would fall back to the still
    // persisted engine and re-enable the tool. This flips the Type Engine page's button to Built-in.
    PyToolTypeEnginePreview.getInstance(project).stagedEnginePackage.set("")
  }

  override fun isUpgradeAvailable(row: ToolRow): Boolean = uv.isUpgradeAvailable(row)
  override fun upgradeTargetVersion(row: ToolRow): String? = uv.latestVersionFor(row)

  override fun onRowExpanded(row: ToolRow) {
    probeRow(row)
  }

  override fun browsePath(row: ToolRow) {
    row.browseExecutablePath(project, view) { chosen -> setCustomPath(row, chosen.toString()) }
  }

  override fun installOnPath(row: ToolRow): Unit = uv.installTool(row, PyToolActionSource.SETTINGS_TABLE)

  override fun upgradeOnPath(row: ToolRow): Unit = uv.upgradeTool(row, PyToolActionSource.SETTINGS_TABLE)

  override fun resetPath(row: ToolRow): Unit = setCustomPath(row, "")

  override fun installIntoSdk(row: ToolRow, sdk: Sdk, anchor: JComponent): Unit =
    uv.installIntoSdkChoosingGroup(row, sdk, anchor, PyToolActionSource.SETTINGS_TABLE)

  /** Route a chosen/cleared custom path through the standard re-probe + refresh flow. */
  private fun setCustomPath(row: ToolRow, value: String) {
    val trimmed = value.trim()
    row.staged = row.staged.copy(customPath = trimmed.takeIf { it.isNotEmpty() }?.let { Path.of(it) })
    probeRow(row, isCustomEdit = true)
    refreshRow(row)
  }

  // ---------- Lifecycle (delegated from the configurable) ----------

  fun onShown(scope: CoroutineScope) {
    this.scope = scope
    // Clear any leftover ✓ from a previous settings session — keeping it would mislead the user
    // about whether the underlying tool state is still up to date.
    rows.forEach { it.lastSuccessMessage = null }
    rows.forEach { probeRow(it) }
    scope.launch { probeAllSdks() }
  }

  /**
   * Single read-action snapshot of the project's Python SDKs, reused to compute [SdkAvailability]
   * for every row (avoids each tool re-touching the project model).
   */
  private suspend fun probeAllSdks() {
    val sdks = withContext(Dispatchers.IO) { snapshotProjectSdks(project) }
    for (row in rows) {
      val avail = withContext(Dispatchers.IO) { row.tool.detectInSdks(sdks) }
      withContext(Dispatchers.Main) {
        row.sdkAvailability = avail
        refreshRow(row)
      }
    }
  }

  /** True iff any row has unsaved edits — a staged enable/path diff, or a dirty detail configurable. */
  fun isModified(): Boolean = rows.any { row ->
    row.staged != snapshotOf(row.tool) || row.detail?.isModified() == true
  }

  /** Persist all rows' staged state into [PyToolsState] and apply any dirty detail configurables. */
  fun apply() {
    checkNoPathErrors(rows)
    val state = PyToolsState.getInstance(project)
    rows.forEach { row ->
      val current = snapshotOf(row.tool)
      val detailModified = row.detail?.isModified() == true
      val rowChanged = row.staged != current || detailModified
      if (row.staged.enabled != current.enabled) {
        state.setEnabled(row.tool, row.staged.enabled)
        row.tool.onEnabledChanged(project, row.staged.enabled)
      }
      if (row.staged.customPath != current.customPath) {
        row.tool.setCustomExecutablePath(eelDescriptor, row.staged.customPath)
      }
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
        PyToolUsagesCollector.Helper.logConfigurationChanged(
          project = project,
          tool = row.tool,
          source = PyToolActionSource.SETTINGS_TABLE,
        )
      }
    }
    rows.forEach { refreshRow(it) }
  }

  /** Revert all rows' staged state to the persisted snapshot and reset any open detail configurables. */
  fun reset() {
    rows.forEach { row ->
      row.staged = stagedFor(row.tool)
      row.detail?.reset()
      // Re-probe so the path field / version reflect the reverted path, and clear any stale error
      // from a rejected custom edit (a non-custom probe never clears it on its own).
      row.pathError = null
      probeRow(row)
    }
    rows.forEach { refreshRow(it) }
  }

  fun disposeUIResources() {
    Disposer.dispose(engineObserverDisposable)
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
    row.probeVersion(scope, project, isCustomEdit, onUpdated = ::refreshRow)
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

  private fun snapshotOf(tool: PyTool): RowState {
    val state = PyToolsState.getInstance(project)
    return RowState(
      enabled = state.isEnabled(tool),
      customPath = tool.getCustomExecutablePath(eelDescriptor),
    )
  }

  /**
   * Initial (and post-reset) editing state for a row: the persisted [snapshotOf], but with the enable
   * toggle shown **on** when the tool is the project's current type engine ([isEngineFor]). This reflects
   * the engine selection as a pending "enabled" edit; since [snapshotOf] stays the modified/apply
   * baseline, the edit is detected and persisted on Apply, and discarded if not applied.
   */
  private fun stagedFor(tool: PyTool): RowState {
    val persisted = snapshotOf(tool)
    return if (!persisted.enabled && isEngineFor(tool)) persisted.copy(enabled = true) else persisted
  }

  /**
   * Whether [tool] is the project's current type engine: the **staged** selection published by an open
   * Type Engine page ([PyToolTypeEnginePreview]) when present, else the persisted engine.
   */
  private fun isEngineFor(tool: PyTool): Boolean {
    val staged = PyToolTypeEnginePreview.getInstance(project).stagedEnginePackage.get()
    return if (staged != null) staged == tool.packageName.name else tool.isSelectedAsTypeEngine(project)
  }
}
