// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion.communityToUnified

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.BannerStartPagePromoter
import com.intellij.openapi.wm.StartPagePromoter
import com.intellij.pycharm.community.ide.impl.promotion.icons.PycharmCommunityIdeImplPromotionIcons
import com.intellij.ui.components.panels.BackgroundRoundedPanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.util.IconUtil
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.RoundRectangle2D
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.min

@ApiStatus.Internal
internal class PyCommunityToUnifiedWelcomeScreenBanner : BannerStartPagePromoter() {
  override val headerLabel: @Nls String = ""
  override val actionLabel: @Nls String = ""
  override val description: @Nls String = ""
  override val promoImage: Icon = PycharmCommunityIdeImplPromotionIcons.Backgrounds.Promotion_bg

  private val mainBannerPanel = createMainPanel()

  val container: Wrapper = Wrapper(mainBannerPanel).apply { isVisible = false }

  override fun getPromotion(isEmptyState: Boolean): JComponent {
    val service = service<PyCommunityToUnifiedPromoService>()
    service.serviceScope.launch {
      val shouldShowBanner = service.shouldShowWelcomeScreenBanner()
      if (shouldShowBanner) {
        withContext(Dispatchers.EDT) {
          container.setContent(mainBannerPanel)
          container.isVisible = true
          container.revalidate()
          container.repaint()
        }
      }
    }
    return container
  }

  override fun runAction() {}

  override fun canCreatePromo(isEmptyState: Boolean): Boolean {
    return PlatformUtils.isPyCharmCommunity()
  }

  override fun getPriorityLevel(): Int = StartPagePromoter.PRIORITY_LEVEL_HIGH

  private fun createMainPanel(): JPanel {
    val panel = panel {
      customizeSpacingConfiguration(EmptySpacingConfiguration()) {
        row {
          label(PyPromoSharedComponents.headerTitle)
            .applyToComponent {
              font = JBFont.h3().asBold()
            }
        }.customize(customRowGaps = UnscaledGapsY(top = 12))
        row {
          text(PyPromoSharedComponents.mainText)
            .applyToComponent {
              foreground = PyPromoSharedComponents.infoFontColor
            }
        }.customize(customRowGaps = UnscaledGapsY(top = 4, bottom = 8))
      }
      row {
        button(PyPromoSharedComponents.updateNow) {
          PyCommunityToUnifiedShowPromoActivity.launchUpdateDialog(null)
        }.applyToComponent { putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true) }
          .focused()
          .customize(customGaps = UnscaledGaps(right = 10))
        browserLink(PyPromoSharedComponents.learnMore, PyPromoSharedComponents.LEARN_MORE_URL)
      }.topGap(TopGap.NONE)
    }.apply {
      isOpaque = false
      border = JBUI.Borders.empty(0, 12, 16, 0)
    }
    return wrapIntoSvgBackgroundPanel(panel, JBUI.scale(16), promoImage)
  }

  private fun wrapIntoSvgBackgroundPanel(panel: JPanel, arc: Int, bgImage: Icon): JPanel {
    val svgPanel = SvgBackgroundRightAnchoredPanel(arc, bgImage).apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
    }
    svgPanel.add(panel)
    return svgPanel
  }

  private class SvgBackgroundRightAnchoredPanel(
    private val arc: Int,
    private val bgImage: Icon,
  ) : BackgroundRoundedPanel(arc) {

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      if (width <= 0 || height <= 0) return

      val iw = bgImage.iconWidth
      val ih = bgImage.iconHeight
      if (iw <= 0 || ih <= 0) return

      val g2 = g.create() as Graphics2D
      try {
        g2.clip(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), arc.toFloat(), arc.toFloat()))
        val sx = width.toFloat() / iw
        val sy = height.toFloat() / ih
        val scale = min(sx, sy)

        val scaled = IconUtil.scale(bgImage, this, scale)
        // Right-anchored (no padding)
        val x = width - scaled.iconWidth
        val y = 0
        scaled.paintIcon(this, g2, x, y)
      }
      finally {
        g2.dispose()
      }
    }
  }
}