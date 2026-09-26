// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.performancePlugin.remotedriver.dataextractor

import com.intellij.driver.model.TextData
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.font.GlyphVector
import java.text.AttributedCharacterIterator
import kotlin.math.cos
import kotlin.math.sin

internal class DataExtractorGraphics2d(private val g: Graphics2D,
                                       private val extractionData: MutableList<TextData>,
                                       private val location: Point,
                                       private val textToKey: TextToKeyCache) : ExtractorGraphics2d(g) {
  private fun addTextData(text: String, x: Int, y: Int) {
    if (rotation != null) {
      val a = rotation!!.theta
      val rx = x * cos(a) - y * sin(a)
      val ry = -x * sin(a) + y * cos(a)
      extractionData.add(TextData(text, Point(location.x + rx.toInt(), location.y + ry.toInt()), TextToKeyCache.findKey(text)))
    }
    else { // shift point a little deeper to the text from right-left conner +3 -3
      extractionData.add(TextData(text, Point(location.x + x + 3, location.y + y - 3), TextToKeyCache.findKey(text)))
    }
  }

  override fun create(): Graphics {
    return DataExtractorGraphics2d(g.create() as Graphics2D, extractionData, location.clone() as Point, textToKey)
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
    location.x = location.x + x
    location.y = location.y + y

    g.translate(x, y)
  }

  override fun translate(tx: Double, ty: Double) {
    location.x = location.x + tx.toInt()
    location.y = location.y + ty.toInt()

    g.translate(tx, ty)
  }

  data class Rotation(val theta: Double, val x: Double? = null, val y: Double? = null)

  private var rotation: Rotation? = null
  override fun rotate(theta: Double) {
    g.rotate(theta)
    rotation = Rotation(theta)
  }

  override fun rotate(theta: Double, x: Double, y: Double) {
    g.rotate(theta, x, y)
    rotation = Rotation(theta, x, y)
  }

  override fun drawGlyphVector(g: GlyphVector, x: Float, y: Float) {
    addTextData(getTextByGlyphVector(g), x.toInt(), y.toInt())
  }
}