package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.streams.ui.LinkedValuesMapping
import com.intellij.debugger.streams.ui.ValueWithPosition
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import java.awt.*
import javax.swing.JPanel

/**
 * @author Vitaliy.Bibaev
 */
class MappingPane(private val beforeValues: List<ValueWithPosition>,
                  private val mapping: LinkedValuesMapping) : JPanel(BorderLayout()) {
  companion object {
    val SELECTED_LINK_COLOR: JBColor = JBColor.BLUE
    val REGULAR_LINK_COLOR: JBColor = JBColor.DARK_GRAY

    val MAX_ANGLE_TO_DRAW_LINK = 3 * Math.PI / 8
    val STROKE = BasicStroke(2.toFloat())
  }

  init {
    // TODO: fix this workaround
    add(JBLabel(" "), BorderLayout.NORTH)
    add(MyDrawPane(), BorderLayout.CENTER)
  }

  private inner class MyDrawPane : JPanel() {
    override fun paintComponent(g: Graphics?) {
      if (g == null) {
        return
      }

      if (g is Graphics2D) {
        g.stroke = STROKE
      }

      val x1 = x
      val x2 = x + width
      for (value in beforeValues) {
        val position: Int = value.position
        val linkedValues = mapping.getLinkedValues(value) ?: continue
        for (nextValue in linkedValues) {
          if (needToDraw(x1, x2, value, nextValue)) {
            g.color = getLineColor(value, nextValue)
            g.drawLine(x1, position, x2, nextValue.position)
          }
        }
      }
    }

    private fun needToDraw(x1: Int, x2: Int, left: ValueWithPosition, right: ValueWithPosition): Boolean {
      if (left.isVisible && right.isVisible) {
        return true
      }

      if (!left.isVisible && !right.isVisible) {
        return false
      }

      if (left.position == -1 || right.position == -1) {
        return false
      }

      return angleToNormal(x1, left.position, x2, right.position) < MAX_ANGLE_TO_DRAW_LINK
    }

    private fun angleToNormal(x1: Int, y1: Int, x2: Int, y2: Int): Double {
      return Math.atan(Math.abs((y2 - y1).toDouble()) / (x2 - x1).toDouble())
    }

    private fun getLineColor(left: ValueWithPosition, right: ValueWithPosition): JBColor {
      if (left.isSelected || right.isSelected) {
        return SELECTED_LINK_COLOR
      }

      return REGULAR_LINK_COLOR
    }
  }
}
