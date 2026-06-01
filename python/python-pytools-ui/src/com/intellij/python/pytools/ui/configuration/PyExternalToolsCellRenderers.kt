// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui.configuration

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBoxTableRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import com.intellij.python.pytools.ui.PyToolsUiBundle
import com.intellij.python.pytools.ui.icons.PythonPytoolsUIIcons
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.TableCellState
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.OnOffButton
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.util.EventObject
import javax.swing.AbstractCellEditor
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

/**
 * Cross-cutting hover state read by [ToolCellRenderer] / [PathCellRenderer] and the tooltip /
 * cursor / click code paths. Implemented by the configurable; renderers see it through this
 * narrow surface so they don't carry an outer-class reference.
 */
internal interface ToolCellHost {
  val project: Project
  /** View row currently under the mouse for the Tool column; -1 means no hover. */
  val hoveredRow: Int
}

internal interface PathCellHost {
  /** View row currently under the mouse for the Path column; -1 means no hover. */
  val pathHoveredRow: Int
  fun iconKindFor(toolRow: ToolRow?, pathFieldValue: PathFieldValue?): PathIconKind
  /**
   * Latest version uv would upgrade [toolRow]'s tool to, when known (returned by `uv tool list
   * --outdated` on uv 0.10.10+). Non-null only on modern uv where the version is reported
   * up-front; the legacy path has no way to know in advance, so the tooltip falls back to a
   * "Click to try to upgrade" wording.
   */
  fun latestVersionFor(toolRow: ToolRow): String?
}

/**
 * Status provider used by the Lookup-column renderers to decorate each chain step (SDK, PATH,
 * uvx) with a ✓ / ✗ / ◐ glyph. Implemented by the configurable since the renderers don't have
 * direct access to the [UvController] or the project SDK list.
 */
internal interface ModeCellHost {
  fun modeStatusFor(toolRow: ToolRow, mode: ExecutableDiscoveryMode): ChainStepStatus
}

/**
 * Build the chain text for a Lookup-column cell. Returns the decorated (HTML) chain when both
 * the [host] and the row's [ToolRow] are resolvable, the plain chain otherwise, and an empty
 * string when the cell value isn't a [ExecutableDiscoveryMode]. Shared between the combobox
 * and read-only renderers so both go through the same null-handling path.
 */
@Suppress("HardCodedStringLiteral")
private fun renderChainText(host: ModeCellHost?, table: JTable, value: Any?, row: Int): String {
  val mode = value as? ExecutableDiscoveryMode ?: return ""
  val toolRow = (table.model as? ListTableModel<*>)?.getRowValue(row) as? ToolRow
  if (host == null || toolRow == null) return modeChainLabel(mode)
  return modeChainLabel(mode) { host.modeStatusFor(toolRow, it) }
}

/** Renders the enabled state as an [OnOffButton] toggle switch. */
internal class OnOffCellRenderer : TableCellRenderer {
  private val button = OnOffButton().apply { isOpaque = true }

  override fun getTableCellRendererComponent(
    table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
  ): Component {
    button.isSelected = value == true
    button.background = table.background
    return button
  }
}

/** Editor that toggles the [OnOffButton] on a single click and commits immediately. */
internal class OnOffCellEditor : AbstractCellEditor(), TableCellEditor {
  private val button = OnOffButton().apply {
    isOpaque = true
    addActionListener { stopCellEditing() }
  }

  override fun getTableCellEditorComponent(
    table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int,
  ): Component {
    // The click that started editing is forwarded to the button by JTable, which
    // toggles JToggleButton's selected state and fires the actionListener that
    // commits the new value.
    button.isSelected = value == true
    button.background = table.background
    return button
  }

  override fun getCellEditorValue(): Any = button.isSelected

  override fun shouldSelectCell(anEvent: EventObject?): Boolean = false
}

/**
 * Two-label panel renderer: tool name on the left, options summary on the right (smaller
 * gray font). A custom [doLayout] explicitly bounds the options label to the leftover
 * horizontal space so JLabel's built-in ellipsis kicks in when the summary is too long
 * (BorderLayout with an opaque cell renderer doesn't always re-layout reliably under the
 * shared CellRendererPane, so we lay out the children ourselves). The right padding
 * reserves space for the gear icon, which is painted only when this row is hovered.
 */
internal class ToolCellRenderer(private val host: ToolCellHost) : JPanel(null), TableCellRenderer {
  private val nameLabel = JBLabel()
  private val optionsLabel = JBLabel().apply {
    font = font.deriveFont(font.size2D - JBUI.scale(1).toFloat())
  }
  private var paintGear: Boolean = false
  private var gearEnabled: Boolean = true

  init {
    isOpaque = true
    add(nameLabel)
    add(optionsLabel)
  }

  override fun doLayout() {
    val leftPad = JBUI.scale(5)
    val gap = JBUI.scale(8)
    val rightReserve = PythonPytoolsUIIcons.Settings.iconWidth + JBUI.scale(10)
    val nameWidth = nameLabel.preferredSize.width.coerceAtMost((width - leftPad - rightReserve).coerceAtLeast(0))
    nameLabel.setBounds(leftPad, 0, nameWidth, height)
    val optX = leftPad + nameWidth + gap
    val optWidth = (width - optX - rightReserve).coerceAtLeast(0)
    optionsLabel.setBounds(optX, 0, optWidth, height)
  }

  override fun getTableCellRendererComponent(
    table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
  ): Component {
    val toolRow = (table.model as? ListTableModel<*>)?.getRowValue(row) as? ToolRow
    nameLabel.text = toolRow?.tool?.presentableName.orEmpty()
    // Suppress the feature summary for disabled tools — a row that's off should read as
    // "nothing is configured here", not "these features would have been on". For enabled
    // tools, render the bracketed activated-features list when non-empty, or a warning
    // "No features selected" hint when empty so the row reads as "you turned this on but
    // didn't pick anything for it to do".
    val isEnabled = toolRow?.staged?.enabled == true
    val options = toolRow
      ?.takeIf { isEnabled }
      ?.tool?.summaryFor(host.project)
      ?.takeIf { it.isNotBlank() }
    val noFeatures = isEnabled && options == null
    optionsLabel.text = when {
      options != null -> "[$options]"
      noFeatures -> PyToolsUiBundle.message("settings.external.tools.column.no.features")
      else -> ""
    }

    // Search hits are highlighted by a separate spotlight border painted on top of
    // the table — never tint the cell background, so click-driven selection (which we
    // suppress anyway) and search highlights both leave the cell looking normal.
    background = table.background
    nameLabel.foreground = table.foreground
    optionsLabel.foreground = if (noFeatures) NO_FEATURES_FOREGROUND else ENABLED_OPTIONS_FOREGROUND

    // Disabled tools have no actionable affordances — the gear is hidden, the click
    // handler exits early, and the cursor stays default. Same rule mirrored in
    // hover hit-testing and the table mouseClicked listener.
    paintGear = (row == host.hoveredRow) && (toolRow?.staged?.enabled == true)
    gearEnabled = toolRow?.tool?.detailConfigurable != null
    return this
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (paintGear) {
      val base = PythonPytoolsUIIcons.Settings
      val icon = if (gearEnabled) base else IconLoader.getDisabledIcon(base)
      val x = width - icon.iconWidth - JBUI.scale(5)
      val y = (height - icon.iconHeight) / 2
      icon.paintIcon(this, g, x, y)
    }
  }
}

/**
 * Combobox renderer that shows the picked mode followed by the implicit fallback chain in
 * regular text — e.g. picking `Sdk` renders as `Sdk → Path → uvx`, `Path` renders as
 * `Path → uvx`, and `uvx` renders as just `uvx`. Used both for the closed-state cell and the
 * popup items so each option visualizes its own fallback chain.
 */
internal class ModeComboBoxRenderer(private val host: ModeCellHost? = null)
  : ComboBoxTableRenderer<ExecutableDiscoveryMode>(ExecutableDiscoveryMode.entries.toTypedArray()) {
  /**
   * Popup items don't have row context (they render each dropdown option independently), so
   * the default plain-chain text from [getTextFor] is what they show. The closed-state cell
   * and the editor route through [getTableCellRendererComponent] / [getTableCellEditorComponent]
   * — those calls have a row index and post-overwrite the rendered text with a decorated
   * chain label that includes the row's ✓ / ✗ / ◐ glyphs.
   */
  override fun getTextFor(value: ExecutableDiscoveryMode): String = modeChainLabel(value)

  override fun getTableCellRendererComponent(
    table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
  ): Component {
    // Force isSelected=false so the cell never tints blue when the row is search-highlighted —
    // the spotlight border drawn by the table is the only highlight cue we want.
    val component = super.getTableCellRendererComponent(table, value, false, hasFocus, row, column)
    text = renderChainText(host, table, value, row)
    return component
  }

  override fun getTableCellEditorComponent(
    table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int,
  ): Component {
    val component = super.getTableCellEditorComponent(table, value, false, row, column)
    text = renderChainText(host, table, value, row)
    return component
  }
}

/**
 * Plain-label renderer used by the Lookup column when the row is disabled. Renders the same
 * fallback-chain text as [ModeComboBoxRenderer.getTextFor] but skips the dropdown arrow that
 * `ComboBoxTableRenderer.paintComponent` would otherwise overlay, so a disabled row reads as
 * "this is the lookup chain" without offering an inactive combobox affordance.
 */
internal class ModeReadOnlyRenderer(private val host: ModeCellHost? = null) : JBLabel(), TableCellRenderer {
  // Same TableCellState path that [ComboBoxTableRenderer.getTableCellRendererComponent] uses,
  // so the disabled label inherits the exact font, border, foreground, and background the
  // combobox version would render with — keeping enabled and disabled rows pixel-aligned.
  private val cellState = TableCellState()

  init {
    isOpaque = true
  }

  override fun getTableCellRendererComponent(
    table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
  ): Component {
    cellState.collectState(table, false, hasFocus, row, column)
    cellState.updateRenderer(this)
    text = renderChainText(host, table, value, row)
    return this
  }
}

/**
 * Single-label panel renderer for the Path column. A custom [doLayout] explicitly bounds
 * the inner [JBLabel] to `width - rightReserve` so the label's built-in ellipsis kicks in
 * before the text reaches the hover-icon area (relying on `DefaultTableCellRenderer`'s
 * own ellipsis turned out to be unreliable when the font switches between italic and
 * regular). The hover icon is painted on top in [paintComponent].
 */
internal class PathCellRenderer(private val host: PathCellHost) : JPanel(null), TableCellRenderer {
  private val textLabel = JBLabel()
  private val baseFont: Font = textLabel.font
  private val italicFont: Font = baseFont.deriveFont(Font.ITALIC)
  /**
   * Shared animated spinner painted into the action-icon slot while
   * [ToolRow.actionInProgress] is set. Needs [AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED]
   * client property on the table view (set in [PyExternalToolsTable.configureView]) — without
   * it the icon stops animating once it lands in a table-cell renderer.
   */
  private val progressIcon: AnimatedIcon = AnimatedIcon.Default()
  private var pathIcon: PathIconKind = PathIconKind.NONE
  /**
   * Cell-renderer captures the row's action lifecycle as two booleans. Both swap the hover
   * action-icon slot, and both are mutually exclusive with the regular install/upgrade icon:
   *  - [paintInProgress] — set while [ToolRow.actionInProgress] is true (modal is up);
   *  - [paintSuccessCheck] — set when [ToolRow.lastSuccessMessage] is non-null.
   * Both are hover-only — the row goes back to looking normal as soon as the pointer leaves.
   */
  private var paintInProgress: Boolean = false
  private var paintSuccessCheck: Boolean = false
  private var paintEdit: Boolean = false

  init {
    isOpaque = true
    add(textLabel)
  }

  override fun doLayout() {
    val leftPad = JBUI.scale(5)
    // Reserve room on the right for both icons: the always-on-hover browse icon plus the
    // optional action-slot icon — one of install / upgrade / reset / in-progress spinner /
    // success ✓ — so the inner JBLabel ellipsis kicks in before the text collides with either.
    val rightReserve = AllIcons.General.OpenDisk.iconWidth + JBUI.scale(4) +
                       PythonPytoolsUIIcons.Install.iconWidth + JBUI.scale(10)
    val w = (width - leftPad - rightReserve).coerceAtLeast(0)
    textLabel.setBounds(leftPad, 0, w, height)
  }

  override fun getTableCellRendererComponent(
    table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
  ): Component {
    val toolRow = (table.model as? ListTableModel<*>)?.getRowValue(row) as? ToolRow
    // Read from the row's cached `detectedPath` — the actual `findInPath` walk and
    // `--version` probe both run on background coroutines. This way the cell paints
    // synchronously without blocking the EDT.
    val detected = toolRow?.pathFieldValue
    val (text, isAuto) = when (detected) {
      is PathFieldValue.Custom -> detected.path.toString() to false
      is PathFieldValue.AutoDetected -> detected.path.toString() to true
      PathFieldValue.NotFound, null -> "" to true
    }
    textLabel.text = text.ifEmpty { PyToolsUiBundle.message("settings.external.tools.path.not.found") }
    textLabel.font = if (isAuto) italicFont else baseFont
    textLabel.horizontalAlignment = SwingConstants.LEADING

    // Search hits are highlighted by a spotlight border painted on top of the table —
    // keep the cell background neutral here.
    background = table.background
    val pathError = toolRow?.pathError
    val belowMin = toolRow?.belowMinVersionMessage
    textLabel.foreground = when {
      pathError != null && !isAuto -> JBColor.RED
      belowMin != null -> JBColor.ORANGE
      isAuto -> UIUtil.getInactiveTextColor()
      else -> UIUtil.getLabelForeground()
    }

    // Disabled tools have no actionable affordances in the Path cell — neither the browse
    // icon nor the install/upgrade/spinner/✓ shows. All overlays are tied to hover.
    val isHovered = row == host.pathHoveredRow && (toolRow?.staged?.enabled == true)
    paintInProgress = isHovered && toolRow.actionInProgress
    paintSuccessCheck = isHovered && !paintInProgress && toolRow.lastSuccessMessage != null
    pathIcon = if (isHovered && !paintInProgress && !paintSuccessCheck) host.iconKindFor(toolRow, detected) else PathIconKind.NONE
    paintEdit = isHovered
    return this
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    // Icon order from the right edge: browse (shown on hover) is rightmost; the optional
    // action-slot icon (install / upgrade / reset / in-progress spinner / success ✓) sits
    // immediately to its left.
    val rightEdge = width - JBUI.scale(5)
    val browseLeft = if (paintEdit) {
      val browse = AllIcons.General.OpenDisk
      val bx = rightEdge - browse.iconWidth
      val by = (height - browse.iconHeight) / 2
      browse.paintIcon(this, g, bx, by)
      bx
    }
    else {
      rightEdge
    }
    val actionIcon = when {
      paintInProgress -> progressIcon
      paintSuccessCheck -> AllIcons.Actions.Checked
      else -> pathIcon.icon
    }
    if (actionIcon != null) {
      val ax = browseLeft - (if (paintEdit) JBUI.scale(4) else 0) - actionIcon.iconWidth
      val ay = (height - actionIcon.iconHeight) / 2
      actionIcon.paintIcon(this, g, ax, ay)
    }
  }
}
