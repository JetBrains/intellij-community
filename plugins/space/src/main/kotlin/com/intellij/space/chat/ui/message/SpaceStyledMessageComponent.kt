// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.message

import circlet.client.api.mc.MessageStyle
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.*
import javax.swing.JComponent

internal class SpaceStyledMessageComponent(content: JComponent, style: MessageStyle = MessageStyle.PRIMARY) : BorderLayoutPanel() {
  // TODO: create colors for Darcula
  private val lineColor = when (style) {
    MessageStyle.PRIMARY -> Color(0xB3D8F8)
    MessageStyle.SECONDARY -> Color(0xB5B9BD)
    MessageStyle.SUCCESS -> Color(0xC7E3D4)
    MessageStyle.ERROR -> Color(0xECC5C6)
    MessageStyle.WARNING -> Color(0xF1E4C9)
  }

  private val lineWidth
    get() = JBUI.scale(6)
  private val lineCenterX
    get() = lineWidth / 2
  private val yRoundOffset
    get() = lineWidth / 2

  init {
    addToCenter(content)
    isOpaque = false
    border = JBUI.Borders.empty(yRoundOffset, 15, yRoundOffset, 0)
  }

  override fun paint(g: Graphics?) {
    super.paint(g)

    with(g as Graphics2D) {
      setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

      color = lineColor
      stroke = BasicStroke(lineWidth.toFloat() / 2 + 1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL)

      drawLine(lineCenterX, yRoundOffset, lineCenterX, height - yRoundOffset)
    }
  }
}