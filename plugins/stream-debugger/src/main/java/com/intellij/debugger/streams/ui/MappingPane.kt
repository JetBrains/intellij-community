package com.intellij.debugger.streams.ui

import com.intellij.ui.JBColor
import java.awt.Graphics
import javax.swing.JPanel

/**
 * @author Vitaliy.Bibaev
 */
class MappingPane(private val beforeValues: List<ValueWithPosition>) : JPanel() {

  override fun paintComponent(g: Graphics?) {
    if (g == null) {
      return
    }

    val x1 = x
    val x2 = x + width
    for (value in beforeValues) {
      val position: Int = value.position
      for (nextValue in value.nextValues.filter { it.isVisible }) {
        g.color = JBColor.WHITE
        g.drawLine(x1, position, x2, nextValue.position)
      }
    }
  }
}