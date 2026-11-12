package com.intellij.xray.frontend.impl.platform

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaCheckBoxUI
import com.intellij.ui.DrawUtil
import com.intellij.ui.JBColor
import com.intellij.ui.paint.PaintUtil
import com.intellij.util.ui.JBUI
import com.intellij.xray.frontend.api.InlayHintStage
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import javax.swing.AbstractButton
import javax.swing.JComponent

internal class XRayStageCheckBoxUI : DarculaCheckBoxUI() {
  var stage: InlayHintStage? = null
  var isInconsistent: Boolean = false

  override fun drawCheckIcon(
    c: JComponent?,
    g: Graphics2D?,
    b: AbstractButton?,
    iconRect: Rectangle?,
    selected: Boolean,
    enabled: Boolean,
  ) {
    if (b == null) return
    if (g == null) return
    if (iconRect == null) return
    drawStageIcon(g, iconRect, stage ?: InlayHintStage.DISABLED)
  }

  private fun drawStageIcon(graphics2D: Graphics2D, rect: Rectangle, stage: InlayHintStage) {
    PaintUtil.useCopy(graphics2D) { graphics2D ->
      DrawUtil.setupRenderingHints(graphics2D)

      val step = JBUI.scale(3)

      val diameter = JBUI.scale(16)
      val borderWidth = DarculaUIUtil.LW.float
      val x = rect.centerX - diameter / 2
      val y = rect.centerY - diameter / 2

      val outerBorder = Ellipse2D.Double()
      outerBorder.x = x + borderWidth / 2
      outerBorder.y = y + borderWidth / 2
      outerBorder.width = diameter - borderWidth.toDouble()
      outerBorder.height = diameter - borderWidth.toDouble()

      // Filling the inner part if not "Disabled"
      val partOfArcToFill = stage.visibilityRank * 360 / (InlayHintStage.entries.size.toDouble() - 1)
      if (stage.visibilityRank > 0) {
        val arcToFill = Arc2D.Double(x, y, diameter.toDouble(), diameter.toDouble(), 90.0, -partOfArcToFill, Arc2D.PIE)
        graphics2D.color = ACCENT
        if (isInconsistent) {
          graphics2D.stroke = BasicStroke(borderWidth)
          (graphics2D as Graphics2D).clip(arcToFill)
          var fillX = x - diameter / 2
          while (fillX < x + diameter) {
            graphics2D.drawLine(fillX.toInt(), y.toInt() + diameter, fillX.toInt() + diameter / 2, y.toInt())
            fillX += step
          }
          graphics2D.setClip(null)
        } else {
          graphics2D.fill(arcToFill)
        }
      }

      // Filling outer border
      graphics2D.color = BORDER
      graphics2D.stroke = BasicStroke(borderWidth)
      graphics2D.draw(outerBorder)
    }
  }
}

private val ACCENT = JBColor(0x3574F0, 0x3574F0)
private val BORDER = JBColor(0x818594, 0x6F737A)
