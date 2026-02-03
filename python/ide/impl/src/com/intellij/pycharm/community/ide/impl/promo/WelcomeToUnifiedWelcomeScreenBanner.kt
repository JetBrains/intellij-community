// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promo


import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.icons.PycharmCommunityIdeImplIcons
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.util.IconUtil
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JPanel

private const val PREV_BUILD_WAS_PC_PROP = "pycharm.previous.version.was.community"

class WelcomeToUnifiedWelcomeScreenBanner : PyCommunityToUnifiedWelcomeScreenBannerBase() {

  companion object {
    const val BANNER_CLOSED_PROPERTY: String = "WELCOME_TO_UNIFIED_BANNER_CLOSED"
  }

  private fun isBannerClosed(): Boolean = PropertiesComponent.getInstance().getBoolean(BANNER_CLOSED_PROPERTY)
  private fun onBannerClosed() = PropertiesComponent.getInstance().setValue(BANNER_CLOSED_PROPERTY, true)

  override val promoImage: Icon = PycharmCommunityIdeImplIcons.Backgrounds.Promotion_bg
  private val closeIcon = IconUtil.scale(PycharmCommunityIdeImplIcons.Icons.Close, null, 1.1f)

  private val myCloseAction =
    object : DumbAwareAction(PyCharmCommunityCustomizationBundle.message("promotion.welcome.to.unified.close"),
                             PyCharmCommunityCustomizationBundle.message("promotion.welcome.to.unified.close.banner"),
                             closeIcon) {
      override fun getActionUpdateThread() = ActionUpdateThread.EDT
      override fun actionPerformed(e: AnActionEvent) {
        onBannerClosed()
        container.isVisible = false
        container.repaint()
      }
    }

  val container: Wrapper = Wrapper(createMainPanel())

  override fun canCreatePromo(isEmptyState: Boolean): Boolean {
    val isUnified = ApplicationInfo.getInstance().build.productCode.equals("PY", ignoreCase = true)
    return isUnified && !isBannerClosed() && isUpgradeFromCommunityToUnified()
  }

  private fun createMainPanel(): JPanel {
    val panel = panel {
      customizeSpacingConfiguration(EmptySpacingConfiguration()) {
        row {
          label(PyCharmCommunityCustomizationBundle.message("promotion.welcome.to.unified.title"))
            .applyToComponent {
              font = JBFont.h3().asBold()
            }
          actionButton(myCloseAction)
            .applyToComponent {
              setMinimumButtonSize { Dimension(closeIcon.iconWidth, closeIcon.iconHeight) }
            }
            .align(AlignX.RIGHT)
            .align(AlignY.TOP)
            .customize(UnscaledGaps(right = 8))
        }.customize(customRowGaps = UnscaledGapsY(top = 12))
        row {
          text(PyCharmCommunityCustomizationBundle.message("promotion.welcome.to.unified.main.text"))
        }.customize(customRowGaps = UnscaledGapsY(top = 4, bottom = 8))
      }
      row {
        browserLink(PyCharmCommunityCustomizationBundle.message("promotion.update.to.unified.learn.more.hyper.link"),
                    "https://blog.jetbrains.com/pycharm/2025/04/unified-pycharm/")
      }.topGap(TopGap.NONE)
    }.apply {
      isOpaque = false
      border = JBUI.Borders.empty(0, 12, 16, 0)
    }
    return panel
  }

  override fun getMainPanel(): JPanel = container

  private fun isUpgradeFromCommunityToUnified(): Boolean {
    val currentCode = ApplicationInfo.getInstance().build.productCode

    val prevWasCommunity = PropertiesComponent.getInstance().getBoolean(PREV_BUILD_WAS_PC_PROP, false)
    return prevWasCommunity && currentCode.equals("PY", ignoreCase = true)
  }
}

class CommunityToUnifiedListener : AppLifecycleListener {
  override fun appStarted() {
    if (PlatformUtils.isPyCharmCommunity()) {
      PropertiesComponent.getInstance().setValue(PREV_BUILD_WAS_PC_PROP, true)
    }
  }
}