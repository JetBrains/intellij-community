package com.intellij.laf.win10

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI
import com.intellij.openapi.ui.ErrorBorderCapable
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Path2D
import javax.swing.JComboBox
import javax.swing.UIManager
import javax.swing.border.Border

class WinIntelliJComboBoxBorder : Border, ErrorBorderCapable {

  override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {
    val comboBox = c as JComboBox<*>
    val ui = c.ui as? WinIntelliJComboBoxUI ?: return

    val hasFocus = DarculaComboBoxUI.hasComboBoxFocus(comboBox)
    val g2 = g!!.create() as Graphics2D

    try {
      val r = Rectangle(x, y, width, height)
      val isCellRenderer = DarculaUIUtil.isTableCellEditor(c)
      var bw = 1

      if (comboBox.isEnabled) {
        val op = DarculaUIUtil.getOutline(comboBox)
        if (op != null) {
          op.setGraphicsColor(g2, hasFocus)
          bw = if (isCellRenderer) 1 else 2
        }
        else if (comboBox.isEditable) {
          if (hasFocus) {
            g2.color = UIManager.getColor("TextField.focusedBorderColor")
          }
          else {
            g2.color = UIManager.getColor(if (ui.isEditorHover()) "TextField.hoverBorderColor" else "TextField.borderColor")
          }
        }
        else {
          if (ui.isPressed || ui.popup.isVisible) {
            g2.color = UIManager.getColor("Button.intellij.native.pressedBorderColor")
          }
          else if (ui.isHover || hasFocus) {
            g2.color = UIManager.getColor("Button.intellij.native.focusedBorderColor")
          }
          else {
            g2.color = UIManager.getColor("Button.intellij.native.borderColor")
          }
        }

        if (!isCellRenderer) {
          JBInsets.removeFrom(r, JBUI.insets(1))
        }
      }
      else {
        g2.color = UIManager.getColor("Button.intellij.native.borderColor")

        val alpha = if (comboBox.isEditable) 0.35f else 0.47f
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
        JBInsets.removeFrom(r, JBUI.insets(1))
      }

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)

      val border: Path2D = Path2D.Float(Path2D.WIND_EVEN_ODD)
      border.append(r, false)

      val innerRect = Rectangle(r)
      JBInsets.removeFrom(innerRect, JBUI.insets(bw))
      border.append(innerRect, false)

      g2.fill(border)
    }
    finally {
      g2.dispose()
    }
  }

  override fun getBorderInsets(c: Component?): Insets {
    return JBUI.insets(if (DarculaUIUtil.isTableCellEditor(c)) 0 else 1).asUIResource()
  }

  override fun isBorderOpaque(): Boolean {
    return false
  }
}
