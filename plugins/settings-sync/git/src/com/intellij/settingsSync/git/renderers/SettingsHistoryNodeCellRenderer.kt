package com.intellij.settingsSync.git.renderers

import com.intellij.settingsSync.SettingsSyncBundle
import com.intellij.settingsSync.git.record.ChangeRecord
import com.intellij.settingsSync.git.record.HistoryRecord
import com.intellij.settingsSync.git.table.SettingsHistoryTable
import com.intellij.settingsSync.git.table.SettingsHistoryTableRow
import com.intellij.settingsSync.git.table.TitleRow
import com.intellij.util.ui.UIUtil
import icons.SettingsSyncIcons
import org.jetbrains.annotations.Nls
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D

internal class SettingsHistoryNodeCellRenderer : SettingsHistoryCellRenderer() {
  private val nodeCenterX = 20.0
  private val remoteIcon = SettingsSyncIcons.RemoteChanges

  private var row: SettingsHistoryTableRow? = null
  private var isGreyedOut: Boolean = false

  override fun customizeHistoryCellRenderer(table: SettingsHistoryTable, row: SettingsHistoryTableRow, selected: Boolean, hasFocus: Boolean, rowIndex: Int) {
    this.row = row
    this.isGreyedOut = isGreyedOut(table, rowIndex)

    toolTipText = determineToolTipText(row)
  }

  @Nls
  private fun determineToolTipText(row: SettingsHistoryTableRow): String? {
    return if (row is TitleRow) {
      when (row.record.origin) {
        ChangeRecord.ChangeOrigin.Local -> SettingsSyncBundle.message("ui.toolwindow.node.local")
        else -> SettingsSyncBundle.message("ui.toolwindow.node.remote")
      }
    } else null
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val g2 = g.create() as Graphics2D

    g2.color = UIUtil.getLabelDisabledForeground()
    if (isGreyedOut) {
      g2.composite = AlphaComposite.SrcOver.derive(0.6f)
    }

    if (row is TitleRow) {
      drawNode(g2)
    } else {
      drawVerticalLine(g2, nodeCenterX, 0.0, height.toDouble())
    }
  }

  private fun drawNode(g2: Graphics2D) {
    val record = row?.record ?: return
    val centerY = height / 2.0

    if (record.origin == ChangeRecord.ChangeOrigin.Local) {
      drawLocalNode(g2, record, centerY)
    } else {
      drawRemoteNode(g2, record, centerY)
    }
  }

  private fun drawLocalNode(g2: Graphics2D, record: HistoryRecord, centerY: Double) {
    val diameter = if (isOldUI) 6.0 else 5.0
    val radius = diameter / 2
    drawTopBottomLines(g2, record, centerY - radius, centerY + radius)

    val oval = Ellipse2D.Double(nodeCenterX - radius, centerY - radius, diameter, diameter)
    g2.setRenderingHints()
    g2.fill(oval)
  }

  private fun drawRemoteNode(g2: Graphics2D, record: HistoryRecord, centerY: Double) {
    val iconHeight = remoteIcon.iconHeight
    remoteIcon.paintIcon(this, g2, (nodeCenterX - iconHeight / 2).toInt(), (centerY - iconHeight / 2).toInt())

    drawTopBottomLines(g2, record, centerY - iconHeight / 2, centerY + iconHeight / 2)
  }

  private fun drawTopBottomLines(g2: Graphics2D, record: HistoryRecord, topIconPoint: Double, bottomIconPoint: Double) {
    if (isOldUI) return

    when (record.position) {
      HistoryRecord.RecordPosition.MIDDLE -> {
        drawVerticalLine(g2, nodeCenterX, 0.0, topIconPoint)
        drawVerticalLine(g2, nodeCenterX, bottomIconPoint, height.toDouble())
      }
      HistoryRecord.RecordPosition.TOP -> {
        drawVerticalLine(g2, nodeCenterX, bottomIconPoint, height.toDouble())
      }
      HistoryRecord.RecordPosition.BOTTOM -> {
        drawVerticalLine(g2, nodeCenterX, 0.0, topIconPoint)
      }
      HistoryRecord.RecordPosition.SINGLE -> {}
    }
  }

  private fun Graphics2D.setRenderingHints() {
    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
  }

  private fun drawVerticalLine(g2: Graphics2D, x: Double, y1: Double, y2: Double) {
    if (isOldUI) g2.stroke = BasicStroke(2.0f)
    val line = Line2D.Double(x, y1, x, y2)
    g2.draw(line)
  }
}
