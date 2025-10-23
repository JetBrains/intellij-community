// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promo

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.BannerStartPagePromoter
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
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
    val disposable = Disposer.newDisposable("BackgroundPanel")
    val url = WelcomeToUnifiedWelcomeScreenBanner::class.java.getResource("/backgrounds/promotion_bg.svg") ?: return panel
    val backgroundImage = SVGLoader.load(url, 1f)
    val backgroundPanel = BackgroundPanel(BorderLayout(), backgroundImage, disposable)
    backgroundPanel.add(panel, BorderLayout.CENTER)
    backgroundPanel.isOpaque = false
    return backgroundPanel
  }

  private class BackgroundPanel(layout: LayoutManager, private val image: Image, parentDisposable: Disposable) : JPanel(layout) {

    private var disposable: Disposable? = null

    init {
      parentDisposable.whenDisposed { clearResources() }
      installBackground()
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

    override fun getComponentGraphics(g: Graphics?): Graphics? {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(g))
    }

    override fun updateUI() {
      super.updateUI()
      @Suppress("SENSELESS_COMPARISON")
      if (image != null) {
        installBackground()
      }
    }

    private fun installBackground() {
      clearResources()
      disposable = Disposer.newDisposable("BackgroundPanel")
      IdeBackgroundUtil.createTemporaryBackgroundTransform(this, image,
                                                           IdeBackgroundUtil.Fill.SCALE,
                                                           IdeBackgroundUtil.Anchor.MIDDLE_RIGHT,
                                                           1f, JBInsets.emptyInsets(),
                                                           disposable)
    }

    private fun clearResources() {
      disposable?.let { Disposer.dispose(it) }
      disposable = null
    }
  }
}