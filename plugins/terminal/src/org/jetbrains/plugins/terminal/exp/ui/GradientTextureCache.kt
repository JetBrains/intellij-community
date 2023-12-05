// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.ui

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.ui.AppUIUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.Color
import java.awt.Graphics2D
import java.awt.TexturePaint
import kotlin.math.floor

class GradientTextureCache(
  private val scheme: EditorColorsScheme,
  private val colorStartKey: ColorKey,
  private val colorEndKey: ColorKey,
  private val defaultColorStart: Color,
  private val defaultColorEnd: Color
) : Disposable {
  private var texture: TexturePaint? = null

  val colorStart: Color
    get() = scheme.getColor(colorStartKey) ?: defaultColorStart
  private val colorEnd: Color
    get() = scheme.getColor(colorEndKey) ?: defaultColorEnd

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect(this)
    connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
      texture = null
    })
  }

  @RequiresEdt
  fun getTexture(graphics: Graphics2D, width: Int): TexturePaint {
    val realWidth = floor(JBUIScale.sysScale(graphics) * width).toInt()
    return if (realWidth != texture?.image?.width) {
      AppUIUtil.createHorizontalGradientTexture(graphics, colorStart, colorEnd, width).also {
        texture = it
      }
    }
    else texture!!
  }

  override fun dispose() {
    texture = null
  }
}