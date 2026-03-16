// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion.communityToUnified

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.StartPagePromoter
import com.intellij.pycharm.community.ide.impl.icons.PycharmCommunityIdeImplIcons
import com.intellij.pycharm.community.ide.impl.promo.PyCommunityToUnifiedWelcomeScreenBannerBase
import com.intellij.pycharm.community.ide.impl.promotion.communityToUnified.statistics.PyCommunityUnifiedPromoFusCollector
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
internal class PyCommunityToUnifiedWelcomeScreenBanner : PyCommunityToUnifiedWelcomeScreenBannerBase() {
  override val headerLabel: @Nls String = ""
  override val actionLabel: @Nls String = ""
  override val description: @Nls String = ""
  override val promoImage: Icon = PycharmCommunityIdeImplIcons.Backgrounds.Promotion_bg

  private val mainBannerPanel = createMainPanel()

  val container: Wrapper = Wrapper().apply { isVisible = false }

  override fun getPromotion(isEmptyState: Boolean): JComponent {
    val service = service<PyCommunityToUnifiedPromoService>()
    service.serviceScope.launch {
      val shouldShowBanner = service.shouldShowWelcomeScreenBanner()
      if (shouldShowBanner) {
        withContext(Dispatchers.EDT) {
          val wrapped = wrapIntoSvgBackgroundPanel(mainBannerPanel)
          container.setContent(wrapped)
          container.isVisible = true
          PyCommunityUnifiedPromoFusCollector.WelcomeScreenBannerShown.log()
          container.revalidate()
          container.repaint()
        }
      }
    }
    return container
  }

  override fun canCreatePromo(isEmptyState: Boolean): Boolean {
    return PlatformUtils.isPyCharmCommunity()
  }

  override fun getMainPanel(): JPanel = container

  override fun getPriorityLevel(): Int = StartPagePromoter.PRIORITY_LEVEL_HIGH

  private fun createMainPanel(): JPanel =
    panel {
      customizeSpacingConfiguration(EmptySpacingConfiguration()) {
        row {
          label(PyPromoSharedComponents.headerTitle)
            .applyToComponent {
              font = JBFont.h3().asBold()
            }
        }.customize(customRowGaps = UnscaledGapsY(top = 12))
        row {
          text(PyPromoSharedComponents.mainText)
        }.customize(customRowGaps = UnscaledGapsY(top = 4, bottom = 8))
      }
      row {
        button(PyPromoSharedComponents.updateNow) {
          PyCommunityUnifiedPromoFusCollector.WelcomeScreenBannerClicked.log(PyCommunityUnifiedPromoFusCollector.BannerControl.UPDATE_NOW)
          PyCommunityToUnifiedShowPromoActivity.Helper.launchUpdateDialog(null)
        }.applyToComponent { putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true) }
          .focused()
          .customize(customGaps = UnscaledGaps(right = 10))
        link(PyPromoSharedComponents.learnMore) {
          PyCommunityUnifiedPromoFusCollector.WelcomeScreenBannerClicked.log(PyCommunityUnifiedPromoFusCollector.BannerControl.LEARN_MORE)
          PyPromoSharedComponents.learnMoreBrowserAction.invoke()
        }
      }.topGap(TopGap.NONE)
    }.apply {
      isOpaque = false
      border = JBUI.Borders.empty(0, 12, 16, 0)
    }
}