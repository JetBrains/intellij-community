// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promo

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.BannerStartPagePromoter
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.util.SVGLoader
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBSwingUtilities
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.*
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
abstract class PyCommunityToUnifiedWelcomeScreenBannerBase : BannerStartPagePromoter() {

  override val headerLabel: @Nls String = ""
  override val actionLabel: @Nls String = ""
  override val description: @Nls String = ""

  abstract fun getMainPanel(): JPanel

  override fun getPromotion(isEmptyState: Boolean): JComponent {
    val mainPanel = getMainPanel()
    return wrapIntoSvgBackgroundPanel(mainPanel)
  }

  override fun runAction() {}

  fun wrapIntoSvgBackgroundPanel(panel: JPanel): JPanel {
    val url = WelcomeToUnifiedWelcomeScreenBanner::class.java.getResource("/backgrounds/promotion_bg.svg") ?: return panel
    val backgroundImage = SVGLoader.load(url, 1f)
    val backgroundPanel = BackgroundPanel(BorderLayout(), backgroundImage)
    backgroundPanel.add(panel, BorderLayout.CENTER)
    backgroundPanel.isOpaque = false
    backgroundPanel.border = RoundedLineBorder(JBColor.border(), 16, 1)
    return backgroundPanel
  }

  private class BackgroundPanel(layout: LayoutManager, private val image: Image) : JPanel(layout), Disposable {

    // Unfortunately, we don't have a proper disposable hierarchy for BannerStartPagePromoter
    private var disposable: Disposable = Disposer.newDisposable(ApplicationManager.getApplication(),
                                                                "PyCommunityToUnifiedWelcomeScreenBannerBase.BackgroundPanel")

    init {
      installBackground()
    }

    override fun dispose() {
      Disposer.dispose(disposable)
    }

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      val g2 = g.create() as Graphics2D
      try {
        GraphicsUtil.setupAAPainting(g2)
        g2.color = background
        g2.fillRoundRect(0, 0, width, height, 16, 16)
      }
      finally {
        g2.dispose()
      }
    }

    override fun getComponentGraphics(g: Graphics?): Graphics {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(g))
    }

    private fun installBackground() {
      IdeBackgroundUtil.createTemporaryBackgroundTransform(this, image,
                                                           IdeBackgroundUtil.Fill.SCALE,
                                                           IdeBackgroundUtil.Anchor.MIDDLE_RIGHT,
                                                           1f, JBInsets.emptyInsets(),
                                                           disposable)
    }
  }
}