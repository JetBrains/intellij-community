package com.intellij.debugger.streams.ui

import java.awt.Graphics
import javax.swing.JPanel

/**
 * @author Vitaliy.Bibaev
 */
class MappingPane(private val left: MappedValuePositionProvider, private val right: ValuePositionProvider) : JPanel() {

  override fun paintComponent(g: Graphics?) {
    if (g == null) {
      return
    }

    val x1 = x
    val x2 = x + width
    val values: List<ValueWithPosition> = left.getValues()
    for (value in values) {
      val position: Int = value.getPosition()
      val nextValues = left.getMapToNextByValue(value)
      for (nextValue in nextValues) {
        g.drawLine(x1, position, x2, nextValue.getPosition())
      }
    }
  }
}