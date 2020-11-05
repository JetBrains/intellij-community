// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("UndesirableClassUsage")

package com.intellij.space.ui

import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.AvatarUtils
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.ImageUtil.applyQualityRenderingHints
import com.intellij.util.ui.ImageUtil.createImageByMask
import com.intellij.util.ui.JBImageIcon
import com.intellij.util.ui.JBUI
import icons.SpaceIcons
import java.awt.Color
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import javax.swing.Icon
import kotlin.math.min

object SpaceAvatarUtils {
  internal fun buildImageWithStatus(image: BufferedImage, statusColor: Color): BufferedImage {
    val size: Int = min(image.width, image.height)

    val outerD = size / 2.5
    val innerD = size / 3.5
    val greenSize = (size - (innerD + outerD) / 2).toInt()

    val avatarOvalArea = Area(Ellipse2D.Double(0.0, 0.0, size.toDouble(), size.toDouble()))
    val onlineOvalArea = Area(Ellipse2D.Double(greenSize.toDouble(), greenSize.toDouble(), outerD, outerD))
    avatarOvalArea.subtract(onlineOvalArea)

    val circleAvatar = createImageByMask(image, avatarOvalArea)
    val g2 = circleAvatar.createGraphics()
    applyQualityRenderingHints(g2)
    g2.paint = statusColor
    g2.fillOval(size - innerD.toInt(), size - innerD.toInt(), innerD.toInt(), innerD.toInt())
    g2.dispose()
    return circleAvatar
  }

  fun createAvatars(image: BufferedImage): SpaceAvatars {
    return SpaceAvatars.Image(image)
  }

  fun generateAvatars(gradientSeed: String, name: String): SpaceAvatars {
    val generatedImage = AvatarUtils.generateColoredAvatar(gradientSeed, name)
    return createAvatars(generatedImage)
  }
}

sealed class SpaceAvatars {
  abstract val circle: Icon
  abstract val offline: Icon
  abstract val online: Icon

  object MainIcon : SpaceAvatars() {
    override val circle: Icon = SpaceIcons.Main
    override val offline: Icon = SpaceIcons.Main
    override val online: Icon = SpaceIcons.Main
  }

  class Image(private val image: BufferedImage) : SpaceAvatars() {
    private val cache: MutableMap<kotlin.Pair<Color, Int>, JBImageIcon> = mutableMapOf()

    override val circle: Icon by lazy { JBImageIcon(ImageUtil.createCircleImage(image)) }
    override val offline: Icon
      get() = createStatusIcon(Color(224, 85, 85))
    override val online: Icon
      get() = createStatusIcon(Color(98, 181, 67))

    private fun createStatusIcon(color: Color): JBImageIcon {
      val size = JBUI.scale(16)
      return cache.getOrPut(color to size) {
        val hiDpi = ImageUtil.ensureHiDPI(SpaceAvatarUtils.buildImageWithStatus(image, color), ScaleContext.create())
        JBImageIcon(ImageUtil.scaleImage(hiDpi, size, size))
      }
    }
  }
}


