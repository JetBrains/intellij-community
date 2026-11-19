// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.fastapi.common

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PromoFeatureListItem
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.HideableProjectGenerator
import com.intellij.python.community.common.promotion.PyFrameworkPromoProvider
import com.intellij.python.fastapi.common.icons.FastAPICommonIcons
import com.jetbrains.python.PyBundle
import com.jetbrains.python.createPromoPanel
import com.jetbrains.python.newProjectWizard.promotion.PromoProjectGenerator
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JPanel

/** Shows the FastAPI promotion in the New Project Wizard when PyCharm runs in free mode. */
@ApiStatus.Internal
class FastAPIPromoProjectGenerator : PromoProjectGenerator(isPython = true), HideableProjectGenerator {
  @NlsSafe
  override fun getName(): String = "FastAPI"

  override fun getLogo(): Icon = FastAPICommonIcons.FastAPI

  override fun createPromoPanel(): JPanel {
    val descriptionHTML = PyBundle.message("python.promo.fastapi.description.html",
                                           "https://www.jetbrains.com/help/pycharm/fastapi-project.html")
    val features = listOf(
      PromoFeatureListItem(AllIcons.Actions.ReformatCode, PyBundle.message("python.promo.fastapi.code")),
      PromoFeatureListItem(AllIcons.General.Web, PyBundle.message("python.promo.fastapi.endpoints")),
      PromoFeatureListItem(AllIcons.Actions.Execute, PyBundle.message("python.promo.fastapi.run")),
    )
    return createPromoPanel(
      descriptionHTML,
      features,
      onAction = PyFrameworkPromoProvider.getInstance()?.let { provider -> { provider.onNpwTemplateAction() } },
    )
  }

  override fun isHidden(): Boolean = !PluginManagerCore.isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID)
}