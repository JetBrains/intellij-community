// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.ui.presentation

import com.intellij.openapi.editor.colors.EditorColorsManager
import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.awt.geom.Arc2D

internal object GraphicsUtils {
  fun Graphics2D.clearShapeOverEditor(drawShape: Graphics2D.() -> Unit) {
    val originalComposite = composite
    val originalPaint = paint
    composite = AlphaComposite.Src
    color = EditorColorsManager.getInstance().globalScheme.defaultBackground
    drawShape.invoke(this)
    paint = originalPaint
    composite = originalComposite
  }

  fun Graphics2D.clearOvalOverEditor(x: Int, y: Int, width: Int, height: Int) {
    clearShapeOverEditor {
      fillOval(x, y, width, height)
    }
  }

  fun Graphics2D.clearHalfOvalOverEditor(x: Int, y: Int, width: Int, height: Int, upper: Boolean) {
    clearShapeOverEditor {
      fillHalfOval(x, y, width, height, upper)
    }
  }

  fun Graphics2D.fillHalfOval(x: Int, y: Int, width: Int, height: Int, upperHalf: Boolean) {
    val start = when {
      upperHalf -> 180.0
      else -> 0.0
    }
    val arc = Arc2D.Double(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble(), start, 180.0, Arc2D.PIE)
    fill(arc)
  }
}
