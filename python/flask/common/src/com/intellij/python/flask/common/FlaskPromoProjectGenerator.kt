// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.flask.common

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PromoFeatureListItem
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.HideableProjectGenerator
import com.intellij.python.community.common.promotion.PyFrameworkPromoProvider
import com.intellij.python.flask.common.icons.FlaskCommonIcons
import com.jetbrains.python.PyBundle
import com.jetbrains.python.createPromoPanel
import com.jetbrains.python.newProjectWizard.promotion.PromoProjectGenerator
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JPanel

/** Shows the Flask promotion in the New Project Wizard when PyCharm runs in free mode. */
@ApiStatus.Internal
class FlaskPromoProjectGenerator : PromoProjectGenerator(isPython = true), HideableProjectGenerator {
  @NlsSafe
  override fun getName(): String = "Flask"

  override fun getLogo(): Icon = FlaskCommonIcons.Flask

  override fun createPromoPanel(): JPanel {
    val descriptionHTML = PyBundle.message("python.promo.flask.description.html",
                                           "https://www.jetbrains.com/help/pycharm/flask.html")
    val features = listOf(
      PromoFeatureListItem(AllIcons.Actions.ReformatCode, PyBundle.message("python.promo.flask.code")),
      PromoFeatureListItem(AllIcons.FileTypes.Html, PyBundle.message("python.promo.flask.templates")),
      PromoFeatureListItem(AllIcons.General.Web, PyBundle.message("python.promo.flask.endpoints")),
    )
    return createPromoPanel(
      descriptionHTML,
      features,
      onAction = PyFrameworkPromoProvider.getInstance()?.let { provider -> { provider.onNpwTemplateAction() } },
    )
  }

  override fun isHidden(): Boolean = !PluginManagerCore.isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID)
}