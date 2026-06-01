// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui.configuration

import com.intellij.icons.AllIcons
import com.intellij.ide.setToolTipText
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.PyToolsState
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import com.intellij.python.pytools.statistics.PyToolUsagesCollector
import com.intellij.python.pytools.statistics.PyToolActionSource
import com.intellij.python.pytools.ui.PyToolsUiBundle
import com.intellij.python.pytools.ui.icons.PythonPytoolsUIIcons
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BasicStroke
import java.awt.Component
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

/**
 * Self-contained component for the External Tools page's table. Owns:
 *  - the row list (built from the `PyTool` extension point) and its [model] / [view];
 *  - all click handlers — gear → detail dialog, browse icon → file picker, install/upgrade icons → uv;
 *  - lifecycle hooks [onShown] / [isModified] / [apply] / [reset] / [disposeUIResources] that the
 *    configurable delegates to.
 *
 * The configurable supplies [project] and the [uv] controller (used for icon-kind hit-test and
 * install/upgrade dispatch). The probe-launching scope arrives lazily via [onShown], typically
 * driven by `JComponent.launchOnShow`, so probes naturally live for the panel's showing-lifetime
 * and are cancelled automatically when the page is hidden.
 */
internal class PyExternalToolsTable(
  override val project: Project,
  private val uv: UvController,
) : TooltipHost {

  /** Source-of-truth row list, materialised once from the [PyTool] extension point. */
  private val rows: List<ToolRow> = PyTool.EP_NAME.extensionList
    .sortedBy { it.presentableName.lowercase() }
    .map { ToolRow(it, snapshotOf(it)) }

  /**
   * The active showing-scope, set by [onShown] and replaced on each hide/show cycle. Used to
   * launch path/version probes; `null` before the first show.
   */
  private var scope: CoroutineScope? = null

  // ---------- ToolCellHost / PathCellHost (combined as TooltipHost) ----------

  /** View row currently under the mouse for the Tool column; -1 means no hover. */
  override var hoveredRow: Int = -1
    private set

  /** View row currently under the mouse for the Path column; -1 means no hover. Drives the install-via-uv icon. */
  override var pathHoveredRow: Int = -1
    private set

  override fun iconKindFor(toolRow: ToolRow?, pathFieldValue: PathFieldValue?): PathIconKind =
    iconKindFor(toolRow, pathFieldValue, uv.uvAvailable.get(), uv::isUvManaged, uv::isUpgradeAvailable)

  override fun latestVersionFor(toolRow: ToolRow): String? = uv.latestVersionFor(toolRow)

  /**
   * Per-step availability for the Lookup column glyphs. SDK comes from the row's enumerated
   * project SDKs, PATH from the row's resolved [PathFieldValue], uvx from the [UvController].
   */
  override fun modeStatusFor(toolRow: ToolRow, mode: ExecutableDiscoveryMode): ChainStepStatus = when (mode) {
    ExecutableDiscoveryMode.INTERPRETER -> toolRow.sdkAvailability.toChainStatus()
    ExecutableDiscoveryMode.PATH -> toolRow.pathFieldValue.toChainStatus()
    ExecutableDiscoveryMode.UVX -> uvxChainStatus(uv.uvAvailable.get())
  }

  // ---------- Renderers, columns, model, view ----------

  private val toolCellRenderer = ToolCellRenderer(this)
  private val pathCellRenderer = PathCellRenderer(this)

  val model: ListTableModel<ToolRow> = ListTableModel(
    arrayOf(EnabledColumn(), ToolColumn(), ModeColumn(), PathColumn()),
    rows,
  )

  val view: TableView<ToolRow> = object : TableView<ToolRow>(model) {
    /**
     * Position-aware tooltips: surface a different popup when the pointer is over the
     * Tool/Path column's hover icon than when it is over the cell text. JTable's default
     * implementation forwards to the cell renderer; we bypass it so the icon hit-test
     * uses the live cell geometry instead of stale renderer state.
     */
    override fun getToolTipText(event: MouseEvent): String? =
      resolveCellTooltip(event, this, rows, this@PyExternalToolsTable) { super.getToolTipText(event) }

    /**
     * Suppress click- and keyboard-driven row selection: a "current row" carries no
     * meaning in this settings table and the visual highlight is misleading. Selection
     * is reserved for search hits — both [selectForSearchHit] and the `TableSpeedSearch`
     * installed below call `setRowSelectionInterval` / `getSelectionModel().setSelectionInterval`
     * directly, which bypass this method.
     */
    override fun changeSelection(rowIndex: Int, columnIndex: Int, toggle: Boolean, extend: Boolean) {
      // intentionally no-op
    }

    /**
     * Search hits are drawn as a rounded "spotlight" border around the matching row,
     * matching the same orange the IntelliJ Settings page uses to flag search matches
     * (see `com.intellij.openapi.options.ex.GlassPanel`). Painted after the default
     * table content so the border sits on top of cell rendering.
     */
    override fun paint(g: Graphics) {
      super.paint(g)
      val row = selectedRow
      if (row < 0) return
      val first = getCellRect(row, 0, true)
      val last = getCellRect(row, columnCount - 1, true)
      val rowRect = Rectangle(first.x, first.y, last.x + last.width - first.x, first.height)
      val g2 = g.create() as Graphics2D
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = searchSpotlightBorderColor()
        g2.stroke = BasicStroke(JBUIScale.scale(2f))
        val arc = JBUI.scale(8)
        val inset = JBUI.scale(2)
        g2.drawRoundRect(
          rowRect.x + inset,
          rowRect.y + inset,
          rowRect.width - 2 * inset,
          rowRect.height - 2 * inset,
          arc,
          arc,
        )
      }
      finally {
        g2.dispose()
      }
    }

    /**
     * Enabled tools render the entire row on the table's stripe color to highlight them as
     * the active set; disabled tools fall through to the regular table background. The cell
     * renderers themselves unconditionally set their background to `table.background`; doing
     * the override here keeps that contract intact for the disabled case while also covering
     * renderers we don't directly own (the `OnOffButton` toggle and the `ComboBoxTableRenderer`
     * used by the Lookup column).
     */
    override fun prepareRenderer(renderer: TableCellRenderer, row: Int, column: Int): Component {
      val component = super.prepareRenderer(renderer, row, column)
      val toolRow = rows.getOrNull(row)
      if (toolRow != null && toolRow.staged.enabled) {
        component.background = UIUtil.getDecoratedRowColor()
      }
      return component
    }
  }

  init {
    configureView()
  }

  private fun configureView() = view.apply {
    setShowGrid(false)
    tableHeader = null
    rowSelectionAllowed = true
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    // Path is the last column and absorbs whatever horizontal space is left after the fixed-width ones.
    autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
    // Paint the table's background through to the bottom of the viewport so the empty area
    // below the last row doesn't show the parent panel's grey.
    fillsViewportHeight = true
    putClientProperty("JTable.autoStartsEdit", true)
    // Lets AnimatedIcon keep ticking once it lands in a cell renderer — without this client
    // property the spinner painted by [PathCellRenderer] freezes on the first frame.
    putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
    // Register with ToolTipManager so it polls our `getToolTipText(MouseEvent)` override.
    // An empty placeholder is enough — actual text comes from the override.
    setToolTipText(HtmlChunk.text(""))
    val toggleWidth = JBUI.scale(60)
    columnModel.getColumn(COL_ENABLED).apply {
      minWidth = toggleWidth
      maxWidth = toggleWidth
      preferredWidth = toggleWidth
    }
    // Pin Tool and Lookup to fixed widths so they don't redistribute on dialog resize; the
    // Path column (last, flexible via [JTable.AUTO_RESIZE_LAST_COLUMN]) absorbs all extra
    // horizontal space.
    columnModel.getColumn(COL_TOOL).apply {
      val width = JBUI.scale(220)
      minWidth = width
      maxWidth = width
      preferredWidth = width
    }
    columnModel.getColumn(COL_MODE).apply {
      val width = JBUI.scale(170)
      minWidth = width
      maxWidth = width
      preferredWidth = width
    }
    columnModel.getColumn(COL_PATH).apply {
      preferredWidth = JBUI.scale(200)
    }
    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        // Selection in this table is reserved for search highlights — clear it on any
        // click so the highlight doesn't linger once the user starts interacting.
        if (selectedRow >= 0) clearSelection()
      }

      override fun mouseClicked(e: MouseEvent) {
        if (e.button != MouseEvent.BUTTON1) return
        val viewRow = rowAtPoint(e.point)
        val viewCol = columnAtPoint(e.point)
        if (viewRow < 0) return
        if (viewCol == COL_TOOL) {
          // Single-click on the gear hot-spot, or double-click anywhere in the cell,
          // opens the detail dialog. Disabled tools surface no actions — including double
          // click — to match the missing gear icon and keep the row visually inert.
          val row = rows[viewRow]
          if (row.tool.detailConfigurable == null || !row.staged.enabled) return
          val onGear = isOverGearIcon(e, viewRow)
          if (onGear || e.clickCount == 2) {
            openDetailDialog(row)
          }
        }
        else if (viewCol == COL_PATH) {
          // Edit icon opens a file picker so the user browses to an executable instead of
          // typing one. The action icon (install/upgrade/info) sits to its right.
          if (isOverPathEditIcon(e, viewRow)) {
            browsePathFor(rows[viewRow])
          }
          else if (isOverPathIcon(e, viewRow)) {
            // The ✓ overlay (after a successful action) takes over the action-icon slot; it
            // shouldn't trigger another install/upgrade when clicked.
            if (rows[viewRow].lastSuccessMessage != null) return
            when (pathIconAtHover(viewRow)) {
              PathIconKind.INSTALL -> uv.installViaUv(rows[viewRow], PyToolActionSource.SETTINGS_TABLE)
              PathIconKind.UPGRADE -> uv.upgradeViaUv(rows[viewRow], PyToolActionSource.SETTINGS_TABLE)
              PathIconKind.RESET -> resetPathFor(rows[viewRow])
              else -> Unit
            }
          }
        }
      }

      override fun mouseExited(e: MouseEvent) {
        // Mouse left the table — clear hover so the icon disappears.
        if (hoveredRow >= 0) {
          val old = hoveredRow
          hoveredRow = -1
          repaintToolCell(old)
        }
        if (pathHoveredRow >= 0) {
          val old = pathHoveredRow
          pathHoveredRow = -1
          repaintPathCell(old)
        }
        cursor = Cursor.getDefaultCursor()
      }
    })
    addMouseMotionListener(object : MouseMotionAdapter() {
      override fun mouseMoved(e: MouseEvent) {
        val viewRow = rowAtPoint(e.point)
        val viewCol = columnAtPoint(e.point)
        val effective = if (viewCol == COL_TOOL && viewRow >= 0) viewRow else -1
        if (hoveredRow != effective) {
          val old = hoveredRow
          hoveredRow = effective
          if (old >= 0) repaintToolCell(old)
          if (effective >= 0) repaintToolCell(effective)
        }
        val pathEffective = if (viewCol == COL_PATH && viewRow >= 0) viewRow else -1
        if (pathHoveredRow != pathEffective) {
          val old = pathHoveredRow
          pathHoveredRow = pathEffective
          if (old >= 0) repaintPathCell(old)
          if (pathEffective >= 0) repaintPathCell(pathEffective)
        }
        // Switch to hand cursor while the pointer is over the gear hot-spot, so the icon
        // visibly behaves like a clickable affordance. Tools without settings get a disabled
        // gear and keep the default cursor.
        val overGear = effective >= 0 &&
                       rows[effective].tool.detailConfigurable != null &&
                       isOverGearIcon(e, effective)
        val isIconWithAction = pathIconAtHover(pathEffective).let {
          it == PathIconKind.INSTALL || it == PathIconKind.UPGRADE || it == PathIconKind.RESET
        }
        val overActionableIcon = pathEffective >= 0 &&
                                 isOverPathIcon(e, pathEffective) &&
                                 isIconWithAction
        val overEditIcon = pathEffective >= 0 && isOverPathEditIcon(e, pathEffective)
        // The whole on/off toggle cell is clickable — clicking anywhere in [COL_ENABLED]
        // starts the cell editor which flips the switch. Show the hand cursor over the whole
        // cell so the affordance matches the click behaviour.
        val overToggle = viewCol == COL_ENABLED && viewRow >= 0
        val desired = if (overGear || overActionableIcon || overEditIcon || overToggle) {
          Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        else {
          Cursor.getDefaultCursor()
        }
        if (cursor != desired) cursor = desired
      }
    })
    // Type-to-search inside the table: typing while focus is on the table jumps to the row
    // whose tool name contains the typed substring. Only the Tool column contributes
    // searchable text; other columns return null so they're ignored.
    val speedSearch = TableSpeedSearch.installOn(this) { _, cell ->
      if (cell.column == COL_TOOL && cell.row in rows.indices) rows[cell.row].tool.presentableName else null
    }
    // Drop the spotlight as soon as the speed search popup closes (typically via Escape
    // or losing focus) so the highlight matches the lifetime of the search query, not
    // just the lifetime until the next click.
    speedSearch.addChangeListener { event ->
      if (event.newValue == null) clearSelection()
    }
  }

  // ---------- Lifecycle (delegated from the configurable) ----------

  /**
   * Called by the configurable from within `launchOnShow`'s coroutine. Stores [scope] for
   * click-driven probes and kicks off the initial `--version` probes for every row.
   */
  fun onShown(scope: CoroutineScope) {
    this.scope = scope
    // Clear any leftover ✓ from a previous settings session — keeping it would mislead the
    // user about whether the underlying tool state is still up to date.
    rows.forEach { it.lastSuccessMessage = null }
    rows.forEach { probeRow(it) }
    scope.launch { probeAllSdks() }
  }

  /**
   * Take a single read-action snapshot of the project's Python SDKs and use it to compute
   * [SdkAvailability] for every row. Sharing the snapshot avoids each tool re-touching the
   * project model, and the read action is taken once instead of N times.
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

  /** True iff any row has unsaved edits — either a staged-vs-persisted diff, or a dirty detail configurable. */
  fun isModified(): Boolean = rows.any { it.dirty || it.staged != snapshotOf(it.tool) }

  /** Persist all rows' staged state into [PyToolsState] and apply any dirty detail configurables. */
  fun apply() {
    val state = PyToolsState.getInstance(project)
    rows.forEach { row ->
      val current = snapshotOf(row.tool)
      val rowChanged = row.staged != current || row.dirty
      if (row.staged.enabled != current.enabled) {
        state.setEnabled(row.tool, row.staged.enabled)
        row.tool.onEnabledChanged(project, row.staged.enabled)
      }
      if (row.staged.mode != current.mode) {
        state.setMode(row.tool, row.staged.mode)
      }
      if (row.staged.customPath != current.customPath) {
        state.setCustomPath(row.tool, row.staged.customPath)
      }
      if (row.dirty) {
        try {
          row.detail?.apply()
        }
        catch (e: ConfigurationException) {
          row.detail?.disposeUIResources()
          row.detail = null
          throw e
        }
        row.dirty = false
      }
      if (rowChanged) {
        // Logged after staged enabled/mode/customPath have been committed to `PyToolsState`
        // (and after the optional detail-configurable apply), so the snapshot read by
        // `row.tool.configurationFusSnapshot(project)` reflects the post-commit values.
        PyToolUsagesCollector.Helper.logConfigurationChanged(
          project = project,
          tool = row.tool,
          source = PyToolActionSource.SETTINGS_TABLE,
        )
      }
    }
  }

  /** Revert all rows' staged state to the currently-persisted snapshot and reset any open detail configurables. */
  fun reset() {
    rows.forEach { row ->
      row.staged = snapshotOf(row.tool)
      row.detail?.reset()
      row.dirty = false
    }
    model.fireTableDataChanged()
  }

  /**
   * Dispose detail configurables; the configurable cancels the [validationScope] separately
   * because that scope's ownership lives at the page lifecycle level (the table is a tenant).
   */
  fun disposeUIResources() {
    rows.forEach { it.detail?.disposeUIResources(); it.detail = null }
  }

  // ---------- Search ----------

  /** Return the index of the first row whose tool name contains [needle] (case-insensitive), or -1. */
  fun findMatchingRowIndex(needle: String): Int {
    val lowercased = needle.lowercase()
    return rows.indexOfFirst { it.tool.presentableName.lowercase().contains(lowercased) }
  }

  /** Select [row] and scroll it into view; used to highlight a settings-search hit. */
  fun selectForSearchHit(row: Int) {
    view.selectionModel.setSelectionInterval(row, row)
    view.scrollRectToVisible(view.getCellRect(row, COL_TOOL, true))
  }

  /** Clear the current search-spotlight selection, if any. */
  fun clearSelection() {
    view.clearSelection()
  }

  // ---------- Click handlers (formerly the host interface) ----------

  /** Open the per-tool detail dialog for [toolRow]; refresh the row on commit. */
  private fun openDetailDialog(toolRow: ToolRow) {
    val configurable = toolRow.detail ?: toolRow.tool.detailConfigurable?.invoke(project) ?: return
    toolRow.detail = configurable
    val component = configurable.createComponent() ?: return
    configurable.reset()

    val dialog = object : DialogWrapper(project, true) {
      init {
        title = PyToolsUiBundle.message("settings.external.tools.edit.dialog.title", toolRow.tool.presentableName)
        init()
      }

      override fun createCenterPanel(): JComponent = component

      override fun doOKAction() {
        try {
          configurable.apply()
          super.doOKAction()
        }
        catch (e: ConfigurationException) {
          @Suppress("HardCodedStringLiteral")
          setErrorText(e.localizedMessage)
        }
      }
    }
    if (dialog.showAndGet()) {
      toolRow.dirty = false
      // Refresh the row so the Options summary picks up the freshly applied feature toggles.
      refreshRow(toolRow)
    }
    else {
      configurable.reset()
    }
  }

  /** Browse for an executable, then route the chosen path through the path column's setter. */
  private fun browsePathFor(item: ToolRow) {
    item.browseExecutablePath(project, view) { chosenPath ->
      val rowIndex = rows.indexOf(item)
      if (rowIndex >= 0) {
        model.setValueAt(chosenPath.toString(), rowIndex, COL_PATH)
      }
    }
  }

  /**
   * Clear the row's staged custom path so the row falls back to auto-detection. Routed through
   * the path column's setter — same code path as a successful browse — so the standard re-probe
   * + validation + repaint flow takes over.
   */
  private fun resetPathFor(item: ToolRow) {
    val rowIndex = rows.indexOf(item)
    if (rowIndex >= 0) {
      model.setValueAt("", rowIndex, COL_PATH)
    }
  }

  // ---------- Probe orchestration ----------

  /** Start (or restart) the path-detection + `--version` probe for [item]. */
  private fun probeRow(item: ToolRow, isCustomEdit: Boolean = false) {
    val scope = scope ?: return
    item.probeVersion(scope, isCustomEdit, ::refreshRow)
  }

  /** Fire a single-row table update so the renderer picks up freshly-probed state. */
  fun refreshRow(item: ToolRow) {
    val idx = rows.indexOf(item)
    if (idx >= 0) model.fireTableRowsUpdated(idx, idx)
  }

  /** Fire a table-wide refresh; the configurable invokes this from its uv-state-changed handler. */
  fun fireAllRowsChanged() {
    model.fireTableDataChanged()
  }

  // ---------- Snapshot helper (used by [isModified], [apply], [reset], and initial row construction) ----------

  private fun snapshotOf(tool: PyTool): RowState {
    val state = PyToolsState.getInstance(project)
    return RowState(
      enabled = state.isEnabled(tool),
      mode = state.getMode(tool),
      customPath = state.getCustomPath(tool),
    )
  }

  // ---------- Hit-testing & per-cell repaints ----------

  /**
   * The kind of hover icon the row would currently render in the Path column, if any.
   * Returns [PathIconKind.NONE] when the row is not hovered or has nothing to show.
   */
  private fun pathIconAtHover(viewRow: Int): PathIconKind {
    if (viewRow != pathHoveredRow) return PathIconKind.NONE
    val toolRow = rows.getOrNull(viewRow) ?: return PathIconKind.NONE
    if (!toolRow.staged.enabled) return PathIconKind.NONE
    return iconKindFor(toolRow, toolRow.pathFieldValue)
  }

  /** True if [e]'s x-coordinate falls within the gear-icon area on the Tool column for [viewRow]. */
  private fun isOverGearIcon(e: MouseEvent, viewRow: Int): Boolean {
    if (viewRow != hoveredRow) return false
    val toolRow = rows.getOrNull(viewRow) ?: return false
    if (!toolRow.staged.enabled) return false
    val rect = view.getCellRect(viewRow, COL_TOOL, false)
    val icon = PythonPytoolsUIIcons.Settings
    val iconRight = rect.x + rect.width - JBUI.scale(5)
    val iconLeft = iconRight - icon.iconWidth
    return e.x in iconLeft..iconRight
  }

  /**
   * True if [e]'s x-coordinate falls within the install / upgrade / info icon hit-zone for
   * [viewRow]. The action icon sits to the left of the browse icon.
   */
  private fun isOverPathIcon(e: MouseEvent, viewRow: Int): Boolean {
    val kind = pathIconAtHover(viewRow)
    val icon = kind.icon ?: return false
    val rect = view.getCellRect(viewRow, COL_PATH, false)
    val rightEdge = rect.x + rect.width - JBUI.scale(5)
    // Browse always occupies the rightmost slot when the row is hovered.
    val actionRight = rightEdge - AllIcons.General.OpenDisk.iconWidth - JBUI.scale(4)
    val actionLeft = actionRight - icon.iconWidth
    return e.x in actionLeft..actionRight
  }

  /**
   * True if [e]'s x-coordinate falls within the browse-icon hit-zone of the hovered Path row.
   * Browse always takes the rightmost slot.
   */
  private fun isOverPathEditIcon(e: MouseEvent, viewRow: Int): Boolean {
    if (viewRow != pathHoveredRow) return false
    val toolRow = rows.getOrNull(viewRow) ?: return false
    if (!toolRow.staged.enabled) return false
    val rect = view.getCellRect(viewRow, COL_PATH, false)
    val rightEdge = rect.x + rect.width - JBUI.scale(5)
    val browseLeft = rightEdge - AllIcons.General.OpenDisk.iconWidth
    return e.x in browseLeft..rightEdge
  }

  /** Repaint just the Tool cell of [row] (cheaper than a full table repaint on every mouse move). */
  private fun repaintToolCell(row: Int) {
    if (row < 0) return
    view.repaint(view.getCellRect(row, COL_TOOL, false))
  }

  /** Repaint just the Path cell of [row]. */
  private fun repaintPathCell(row: Int) {
    if (row < 0) return
    view.repaint(view.getCellRect(row, COL_PATH, false))
  }

  // ---------- Column definitions (inner classes so they reach the outer renderers + probe) ----------

  private class EnabledColumn : ColumnInfo<ToolRow, Boolean>(" ") {
    private val cellRenderer = OnOffCellRenderer()
    private val cellEditor = OnOffCellEditor()

    override fun valueOf(item: ToolRow): Boolean = item.staged.enabled
    override fun isCellEditable(item: ToolRow?): Boolean = true
    override fun setValue(item: ToolRow, value: Boolean) {
      item.staged = item.staged.copy(enabled = value)
    }

    override fun getRenderer(item: ToolRow?): TableCellRenderer = cellRenderer
    override fun getEditor(item: ToolRow?): TableCellEditor = cellEditor
  }

  /**
   * Single column that renders both the tool name and a comma-separated summary of the
   * activated options. The name is rendered in normal weight on the left; the bracketed
   * options summary is rendered in a smaller gray font right-aligned next to where the
   * gear icon appears on hover. Clicking the icon (or double-clicking anywhere in the cell)
   * opens the detail dialog.
   */
  private inner class ToolColumn : ColumnInfo<ToolRow, String>(
    PyToolsUiBundle.message("settings.external.tools.column.name")
  ) {
    override fun valueOf(item: ToolRow): String = item.tool.presentableName
    override fun isCellEditable(item: ToolRow?): Boolean = false
    override fun getRenderer(item: ToolRow?): TableCellRenderer = toolCellRenderer
  }

  private inner class ModeColumn : ColumnInfo<ToolRow, ExecutableDiscoveryMode>(
    PyToolsUiBundle.message("settings.external.tools.column.mode")
  ) {
    // Two separate instances on purpose: ComboBoxTableRenderer is a JLabel that mutates its own
    // state when used as a renderer for surrounding cells. Sharing one instance for both renderer
    // and editor leaves the active editor JLabel blank after the surrounding rows repaint.
    private val cellRenderer = ModeComboBoxRenderer(this@PyExternalToolsTable)

    /** Plain-text renderer used for disabled rows — same chain text, no dropdown arrow. */
    private val readOnlyRenderer = ModeReadOnlyRenderer(this@PyExternalToolsTable)
    private val cellEditor = ModeComboBoxRenderer(this@PyExternalToolsTable).withClickCount(1)

    override fun valueOf(item: ToolRow): ExecutableDiscoveryMode = item.staged.mode
    override fun getColumnClass(): Class<*> = ExecutableDiscoveryMode::class.java

    /** Disabled tools have a blank Lookup cell with no combobox affordance. */
    override fun isCellEditable(item: ToolRow?): Boolean = item?.staged?.enabled == true
    override fun setValue(item: ToolRow, value: ExecutableDiscoveryMode) {
      item.staged = item.staged.copy(mode = value)
    }

    override fun getRenderer(item: ToolRow?): TableCellRenderer =
      if (item?.staged?.enabled == true) cellRenderer else readOnlyRenderer

    override fun getEditor(item: ToolRow?): TableCellEditor = cellEditor
  }

  private inner class PathColumn : ColumnInfo<ToolRow, String>(
    PyToolsUiBundle.message("settings.external.tools.column.path")
  ) {
    override fun valueOf(item: ToolRow): String {
      // Returned to the renderer (which actually consults `detectedPath` for display) and to
      // any programmatic `setValueAt` callers. The cell itself is non-editable — see
      // [isCellEditable] — so this path is never typed by the user.
      return item.staged.customPath?.toString().orEmpty()
    }

    /**
     * The cell does not support inline editing: changes come from the browse picker (clicking
     * the OpenDisk hover icon) which routes through `tableModel.setValueAt` → [setValue].
     * Disabling this prevents JTable from launching a text-field editor on double-click.
     */
    override fun isCellEditable(item: ToolRow?): Boolean = false

    override fun setValue(item: ToolRow, value: String) {
      val trimmed = value.trim()
      val newPath = trimmed.takeIf { it.isNotEmpty() }?.let { Path.of(it) }
      item.staged = item.staged.copy(customPath = newPath)
      // The path the row displays just changed (custom edit). Kick off a re-probe so the
      // version + below-min flag refresh; surface validation errors via [ToolRow.pathError].
      probeRow(item, isCustomEdit = true)
    }

    override fun getRenderer(item: ToolRow?): TableCellRenderer = pathCellRenderer
  }
}
