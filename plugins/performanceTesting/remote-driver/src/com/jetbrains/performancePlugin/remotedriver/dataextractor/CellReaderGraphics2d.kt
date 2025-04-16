// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.performancePlugin.remotedriver.dataextractor

import com.intellij.driver.model.TextData
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.font.GlyphVector
import java.text.AttributedCharacterIterator

internal class CellReaderGraphics2d(private val g: Graphics2D, private val extractionData: MutableList<TextData>) :
  ExtractorGraphics2d(g) {

  override fun create(): Graphics {
    return CellReaderGraphics2d(
      g.create() as Graphics2D,
      extractionData
    )
  }

  override fun drawString(str: String?, x: Int, y: Int) {
    if (str != null) {
      addTextData(str, x, y)
    }
    g.drawString(str, x, y)
  }

  override fun drawString(str: String?, x: Float, y: Float) {
    if (str != null) {
      addTextData(str, x.toInt(), y.toInt())
    }
    g.drawString(str, x, y)
  }

  override fun drawString(iterator: AttributedCharacterIterator?, x: Int, y: Int) {
    if (iterator != null) {
      addTextData(iterator.toString(), x, y)
    }
    g.drawString(iterator, x, y)
  }

  override fun drawString(iterator: AttributedCharacterIterator?, x: Float, y: Float) {
    if (iterator != null) {
      addTextData(iterator.toString(), x.toInt(), y.toInt())
    }
    g.drawString(iterator, x, y)
  }


  override fun translate(x: Int, y: Int) {
    g.translate(x, y)
  }

  override fun translate(tx: Double, ty: Double) {
    g.translate(tx, ty)
  }

  override fun rotate(theta: Double) {
    g.rotate(theta)
  }

  override fun rotate(theta: Double, x: Double, y: Double) {
    g.rotate(theta, x, y)
  }

  override fun drawGlyphVector(g: GlyphVector, x: Float, y: Float) {
    addTextData(getTextByGlyphVector(g), x.toInt(), y.toInt())
  }

  private fun addTextData(text: String, x: Int, y: Int) {
    extractionData.add(TextData(text, Point(x, y), null))
  }
}