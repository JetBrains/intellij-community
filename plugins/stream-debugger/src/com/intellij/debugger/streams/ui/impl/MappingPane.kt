// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.streams.ui.LinkedValuesMapping
import com.intellij.debugger.streams.ui.TraceController
import com.intellij.debugger.streams.ui.ValueWithPosition
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import javax.swing.JPanel
import javax.swing.SwingConstants


/**
 * @author Vitaliy.Bibaev
 */
class MappingPane(name: String,
                  fullCallExpression: String,
                  private val beforeValues: List<ValueWithPosition>,
                  private val mapping: LinkedValuesMapping,
                  private val controller: TraceController) : JPanel(BorderLayout()) {
  private companion object {
    val DARCULA_LINE_COLOR = LineColor(regular = JBColor.GRAY,
                                       selected = JBColor.BLUE,
                                       inactive = JBColor({ Color(92, 92, 92) }))
    val INTELLIJ_LINE_COLOR = LineColor(regular = JBColor({ Color(168, 168, 168) }),
                                        selected = JBColor({ Color(0, 96, 229) }),
                                        inactive = JBColor({ Color(204, 204, 204) }))

    val STROKE = BasicStroke(JBUI.scale(1.toFloat()))
  }

  init {
    val label = JBLabel(name, SwingConstants.CENTER)
    label.toolTipText = fullCallExpression
    label.border = JBUI.Borders.empty(2, 0, 3, 0)
    add(label, BorderLayout.NORTH)
    add(MyDrawPane(), BorderLayout.CENTER)
  }

  private inner class MyDrawPane : JPanel() {
    override fun paintComponent(g: Graphics?) {
      if (g == null) {
        return
      }

      if (g is Graphics2D) {
        g.stroke = STROKE

        val config = GraphicsUtil.setupAAPainting(g)

        val colors = if (UIUtil.isUnderDarcula()) DARCULA_LINE_COLOR else INTELLIJ_LINE_COLOR
        if (isSelectedExist()) {
          drawLines(g, colors.inactive, false)
          drawLines(g, colors.selected, true)
        }
        else {
          drawLines(g, colors.regular, false)
        }

        config.restore()
      }
    }

    private fun isSelectedExist(): Boolean = controller.isSelectionExists


    private fun drawLines(g: Graphics2D, color: Color, highlighted: Boolean) {
      val x1 = x
      val x2 = x + width
      g.color = color
      for (value in beforeValues) {
        val linkedValues = mapping.getLinkedValues(value) ?: continue
        for (nextValue in linkedValues) {
          if (needToDraw(value, nextValue) && highlighted == needToHighlight(value, nextValue)) {
            val y1 = value.position
            val y2 = nextValue.position

            g.drawLine(x1, y1, x2, y2)
          }
        }
      }
    }

    private fun needToDraw(left: ValueWithPosition, right: ValueWithPosition): Boolean = left.isVisible || right.isVisible

    private fun needToHighlight(left: ValueWithPosition, right: ValueWithPosition): Boolean = left.isHighlighted && right.isHighlighted
  }

  private data class LineColor(val regular: JBColor, val selected: JBColor, val inactive: JBColor)
}
