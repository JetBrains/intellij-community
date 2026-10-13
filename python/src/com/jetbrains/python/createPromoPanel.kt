// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.ide.BrowserUtil
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PromoFeatureListItem
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.jetbrains.python.icons.PythonIcons
import com.intellij.python.community.common.promotion.PyFrameworkPromoProvider
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Panel that suggests user to buy a Pro version or start a free trial.
 * By default, it shows a "pycharm.free.mode.upgrade.body" promo text.
 * Could be customized by passing descriptionHtml and features list. In this case, default text will be substituted to features list.
 */
@RequiresEdt
fun createPromoPanel(
  @Nls descriptionHtml: String? = null,
  features: List<PromoFeatureListItem>? = null,
  onAction: (() -> Unit)? = null,
  isTrialAvailable: Boolean = onAction != null && PyFrameworkPromoProvider.getInstance()?.isTrialAvailable() == true,
): JPanel {
  val contentPanel = panel {
    row {
      icon(PythonIcons.Python.Pycharm32).align(AlignY.TOP)
      panel {
        row {
          @Suppress("DialogTitleCapitalization") // PyCharm Pro is literally how we want to see it
          text(PyBundle.message("pycharm.free.mode.upgrade.title")).align(AlignY.BOTTOM).applyToComponent {
            font = JBFont.h1()
          }
        }.topGap(TopGap.SMALL)

        if (descriptionHtml != null) {
          row {
            text(descriptionHtml) { BrowserUtil.browse(it.url.toExternalForm()) }
          }
        }

        if (features == null) {
          row {
            text(PyBundle.message("pycharm.free.mode.upgrade.body")).align(AlignY.TOP).applyToComponent {
              putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap(top = true, bottom = false))
            }
          }.topGap(TopGap.SMALL)
        }
        else {
          features.forEach { feature ->
            row {
              icon(feature.icon).gap(RightGap.SMALL).align(AlignY.TOP)
              text(feature.title)
            }
          }
        }
      }
    }
  }

  val actionButtonText = if (isTrialAvailable && onAction != null) {
    PyBundle.message("pycharm.free.mode.start.trial.button")
  }
  else {
    PyBundle.message("pycharm.free.mode.upgrade.button")
  }

  val bottomPanel = panel {
    row {
      button(actionButtonText) {
        if (onAction != null) {
          onAction()
        }
        else {
          BrowserLauncher.instance.open("https://www.jetbrains.com/pycharm/buy/?utm_source=product&utm_medium=referral&utm_campaign=pycharm&utm_content=pro_upgrade&section=commercial&billing=yearly")
        }
      }.applyToComponent {
        putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
      }
    }
  }.apply {
    border = JBUI.Borders.empty(16, 20)
  }

  val mainPanel = JPanel(BorderLayout())
  mainPanel.add(contentPanel, BorderLayout.CENTER)
  mainPanel.add(bottomPanel, BorderLayout.SOUTH)
  return mainPanel
}
