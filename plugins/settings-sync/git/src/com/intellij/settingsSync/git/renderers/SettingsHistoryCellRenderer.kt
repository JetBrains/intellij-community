package com.intellij.settingsSync.git.renderers

import com.intellij.settingsSync.git.table.FileRow
import com.intellij.settingsSync.git.table.SeparatorRow
import com.intellij.settingsSync.git.table.SettingsHistoryTable
import com.intellij.settingsSync.git.table.SettingsHistoryTableRow
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JTable

internal abstract class SettingsHistoryCellRenderer : ColoredTableCellRenderer() {
  companion object {
    @JvmStatic
    protected val isOldUI: Boolean = !ExperimentalUI.isNewUI()
  }

  protected var iconOpacity: Float? = null
  private val tooltipTextFragments: MutableList<MutableList<TooltipTextFragment>> = mutableListOf()


  /**
   * Same as com.intellij.ui.ColoredTableCellRenderer#customizeCellRenderer,
   * but with some basic styling already applied
   */
  abstract fun customizeHistoryCellRenderer(table: SettingsHistoryTable,
                                            row: SettingsHistoryTableRow,
                                            selected: Boolean,
                                            hasFocus: Boolean,
                                            rowIndex: Int)

  final override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
    table as SettingsHistoryTable
    value as SettingsHistoryTableRow

    setCellBorders()
    paintBackground(table, value, selected, table.isFocusOwner)
    customizeHistoryCellRenderer(table, value, selected, table.isFocusOwner, row)
  }

  private fun setCellBorders() {
    setPaintFocusBorder(false)
    setFocusBorderAroundIcon(true)
    border = BorderFactory.createEmptyBorder(1, 1, 1, 1)
  }

  override fun paintIcon(g: Graphics, icon: Icon, offset: Int) {
    val g2 = g as? Graphics2D ?: return super.paintIcon(g, icon, offset)
    g2.withAlphaComposite(iconOpacity) {
      super.paintIcon(this, icon, offset)
    }
  }

  private inline fun Graphics2D.withAlphaComposite(alpha: Float?, block: Graphics2D.() -> Unit) {
    if (alpha == null) {
      block()
    }
    else {
      val originalComposite = this.composite
      this.composite = AlphaComposite.SrcOver.derive(alpha)
      block()
      this.composite = originalComposite
    }
  }

  override fun clear() {
    tooltipTextFragments.clear()
    toolTipText = null
    iconOpacity = null
    super.clear()
  }

  protected fun isExpanded(table: SettingsHistoryTable, row: SettingsHistoryTableRow): Boolean {
    return table.model.expandedRows.contains(row.record.id)
  }

  protected fun isHovered(table: SettingsHistoryTable, row: SettingsHistoryTableRow): Boolean {
    return table.isFocusOwner && row.record == table.hoveredRecord
  }

  protected fun isGreyedOut(table: SettingsHistoryTable, rowIndex: Int): Boolean {
    return table.isFocusOwner && table.isResetHovered && rowIndex < table.hoveredRow
  }

  private fun paintBackground(table: SettingsHistoryTable, row: SettingsHistoryTableRow, selected: Boolean, focused: Boolean) {
    if (row is SeparatorRow) {
      background = JBUI.CurrentTheme.Table.BACKGROUND
      return
    }

    val hovered = isHovered(table, row)

    if (selected) {
      if (row is FileRow) {
        background = JBUI.CurrentTheme.Table.Selection.background(focused)
      }
      else {
        background = JBUI.CurrentTheme.Table.Hover.background(focused)
      }
    }
    else if (hovered) {
      background = JBUI.CurrentTheme.Table.Hover.background(focused)
    }
    else {
      background = JBUI.CurrentTheme.Table.BACKGROUND
    }
  }

  protected fun createLabelIcon(rawIcon: Icon, size: Int) = IconUtil.toSize(rawIcon, JBUI.scale(size), JBUI.scale(size))

  protected fun addTooltipTextFragment(tooltipTextFragment: TooltipTextFragment) {
    if (tooltipTextFragments.isEmpty()) addNewLineToTooltip()
    val currentLine = tooltipTextFragments.last()
    currentLine.add(tooltipTextFragment)
  }

  protected fun addNewLineToTooltip() {
    tooltipTextFragments.add(mutableListOf())
  }

  @Nls
  protected fun buildTooltip(): String? {
    if (tooltipTextFragments.isEmpty()) return null

    val result = StringBuilder("<html><body>")
    for (line in tooltipTextFragments) {
      result.append("<div>")
      line.forEach { result.append(it.buildSpanTag()) }
      result.append("</div>")
    }
    result.append("</body></html>")
    return result.toString()
  }

  protected data class TooltipTextFragment(@Nls val text: String, val isGray: Boolean, val isSmall: Boolean) {
    fun buildSpanTag(): String {
      return """
        <span style="${buildStyleContent()}">$text</span>
      """.trimIndent()
    }

    private fun buildStyleContent(): String {
      val result = StringBuilder()
      if (isGray) {
        val grayColor = SimpleTextAttributes.GRAY_ATTRIBUTES.fgColor.toHexString()
        result.append("color:").append(grayColor).append(";")
      }
      if (isSmall) {
        result.append("font-size:").append(UIUtil.getFontSize(UIUtil.FontSize.SMALL)).append(";")
      }
      return result.toString()
    }

    @NonNls
    private fun Color.toHexString(): String {
      val red = Integer.toHexString(this.red)
      val green = Integer.toHexString(this.green)
      val blue = Integer.toHexString(this.blue)
      return "#${red.padStart(2, '0')}${green.padStart(2, '0')}${blue.padStart(2, '0')}"
    }
  }
}
