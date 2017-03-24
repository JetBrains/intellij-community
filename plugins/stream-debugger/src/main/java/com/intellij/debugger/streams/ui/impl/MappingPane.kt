package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.streams.ui.LinkedValuesMapping
import com.intellij.debugger.streams.ui.ValueWithPosition
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.Graphics
import javax.swing.JPanel

/**
 * @author Vitaliy.Bibaev
 */
class MappingPane(private val beforeValues: List<ValueWithPosition>,
                  private val mapping: LinkedValuesMapping) : JPanel(BorderLayout()) {
  init {
    add(JBLabel("map"), BorderLayout.NORTH)
    add(MyDrawPane(), BorderLayout.CENTER)
  }

  private inner class MyDrawPane : JPanel() {
    override fun paintComponent(g: Graphics?) {
      if (g == null) {
        return
      }

      val x1 = x
      val x2 = x + width
      for (value in beforeValues) {
        if (!value.isVisible) continue
        val position: Int = value.position
        val linkedValues = mapping.getLinkedValues(value) ?: continue
        for (nextValue in linkedValues.filter { it.isVisible }) {
          g.color = JBColor.BLACK
          g.drawLine(x1, position, x2, nextValue.position)
        }
      }
    }
  }
}