// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.ui

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.ui.AppUIUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Paint
import java.awt.TexturePaint
import kotlin.math.floor

internal class GradientTextureCache(
  private val scheme: EditorColorsScheme,
  val colorStartKey: ColorKey,
  val colorEndKey: ColorKey
) : Disposable {
  private var texture: TexturePaint? = null

  private val colorStart: Color?
    get() = scheme.getColor(colorStartKey)
  private val colorEnd: Color?
    get() = scheme.getColor(colorEndKey)

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect(this)
    connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
      texture = null
    })
    connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
      texture = null
    })
  }

  @RequiresEdt
  fun getTexture(graphics: Graphics2D, width: Int): Paint? {
    val realWidth = floor(JBUIScale.sysScale(graphics) * width).toInt()
    if (realWidth != texture?.image?.width) {
      texture = getGradientRange()?.let {
        AppUIUtil.createHorizontalGradientTexture(graphics, it.first, it.second, width)
      }
    }
    return texture
  }

  private fun getGradientRange(): Pair<Color, Color>? {
    val resolvedColorStart: Color? = colorStart
    val resolvedColorEnd: Color? = colorEnd
    return if (resolvedColorStart != null || resolvedColorEnd != null) {
      (resolvedColorStart ?: resolvedColorEnd!!) to (resolvedColorEnd ?: resolvedColorStart!!)
    }
    else null
  }

  override fun dispose() {
    texture = null
  }
}