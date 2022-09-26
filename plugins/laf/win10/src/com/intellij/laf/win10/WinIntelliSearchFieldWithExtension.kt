package com.intellij.laf.win10

import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.UIManager
import javax.swing.plaf.PanelUI

@Suppress("unused")
@ApiStatus.Experimental
class WinIntelliSearchFieldWithExtensionUI : PanelUI() {
  companion object {
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun createUI(c: JComponent?) = WinIntelliSearchFieldWithExtensionUI()
  }

  override fun installUI(c: JComponent?) {
    super.installUI(c)
    c ?: return
    c.border = UIManager.getBorder("SearchFieldWithExtension.border")
    c.background = UIManager.getColor("SearchFieldWithExtension.background")
  }

  override fun paint(g: Graphics?, c: JComponent?) {
    val g2 = g?.create() as Graphics2D
    c ?: return
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
      val parent = c.parent
      if (c.isOpaque && parent != null) {
        g2.color = parent.background
        g2.fillRect(0, 0, c.width, c.height)
      }
      if (c.border is WinIntelliJTextBorder) {
        WinIntelliJTextFieldUI.paintTextFieldBackground(c, g2)
      }
      else if (c.isOpaque) {
        super.paint(g2, c)
      }
    }
    finally {
      g2.dispose()
    }
  }
}
