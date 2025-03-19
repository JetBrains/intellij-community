// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.jetbrains.performancePlugin.remotedriver.dataextractor

import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.font.GlyphVector
import java.text.AttributedCharacterIterator

internal class CellReaderGraphics2d(private val g: Graphics2D, private val extractionData: MutableList<String>) :
  ExtractorGraphics2d(g) {
  private fun addTextData(text: String) {
    extractionData.add(text)
  }

  override fun create(): Graphics {
    return CellReaderGraphics2d(
      g.create() as Graphics2D,
      extractionData
    )
  }

  override fun drawString(str: String?, x: Int, y: Int) {
    if (str != null) {
      addTextData(str)
    }
    g.drawString(str, x, y)
  }

  override fun drawString(str: String?, x: Float, y: Float) {
    if (str != null) {
      addTextData(str)
    }
    g.drawString(str, x, y)
  }

  override fun drawString(iterator: AttributedCharacterIterator?, x: Int, y: Int) {
    if (iterator != null) {
      addTextData(iterator.toString())
    }
    g.drawString(iterator, x, y)
  }

  override fun drawString(iterator: AttributedCharacterIterator?, x: Float, y: Float) {
    if (iterator != null) {
      addTextData(iterator.toString())
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
    addTextData(getTextByGlyphVector(g))
  }
}