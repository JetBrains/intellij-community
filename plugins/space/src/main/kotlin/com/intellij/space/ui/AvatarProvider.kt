package com.intellij.space.ui

import circlet.client.api.TD_MemberProfile
import circlet.client.api.englishFullName
import circlet.platform.api.TID
import com.intellij.space.ui.SpaceAvatarUtils.generateColoredAvatar
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.JBImageIcon
import com.intellij.util.ui.JBValue
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import org.jetbrains.annotations.CalledInAwt
import runtime.Ui
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

class SpaceAvatarProvider(
  private val lifetime: Lifetime,
  private val component: Component,
  val iconSize: JBValue
) {
  private val scaleContext = ScaleContext.create(component)

  private val avatarIconsCache: MutableMap<TID, Icon> = HashMap()

  private val imageLoader: SpaceImageLoader = SpaceImageLoader.getInstance()

  @CalledInAwt
  fun getIcon(user: TD_MemberProfile): Icon {
    val iconSize = iconSize.get()

    // so that icons are rescaled when any scale changes (be it font size or current DPI)
    if (scaleContext.update(ScaleContext.create(component))) {
      avatarIconsCache.clear()
    }

    return avatarIconsCache.getOrPut(user.id) {
      val gradientIcon = resizeIcon(JBImageIcon(ImageUtils.createCircleImage(
        generateColoredAvatar(
          user.username,
          user.englishFullName()
        )
      )), iconSize)
      val icon = DelegatingIcon(gradientIcon)

      user.smallAvatar?.let { tid ->
        launch(lifetime, Ui) {
          val image = imageLoader.loadImageAsync(tid)?.await()
          if (image != null) {
            val circleImage = ImageUtils.createCircleImage(image)
            icon.delegate = resizeIcon(JBImageIcon(circleImage), iconSize)
            component.repaint()
          }
        }
      }

      icon
    }
  }
}

private class DelegatingIcon(var delegate: Icon) : Icon {
  override fun getIconHeight() = delegate.iconHeight
  override fun getIconWidth() = delegate.iconWidth
  override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) = delegate.paintIcon(c, g, x, y)
}
