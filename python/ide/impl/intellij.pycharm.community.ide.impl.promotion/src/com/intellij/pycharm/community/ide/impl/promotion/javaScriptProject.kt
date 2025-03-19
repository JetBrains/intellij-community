// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion

import com.intellij.icons.AllIcons
import com.intellij.ide.wizard.withVisualPadding
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FeaturePromoBundle
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PromoFeaturePage
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PromoPages
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.newProjectWizard.promotion.PromoProjectGenerator
import javax.swing.Icon
import javax.swing.JPanel

@NlsSafe
private const val JAVASCRIPT_NAME = "JavaScript"

internal class JavaScriptPromoProjectGenerator : PromoProjectGenerator(isPython = false) {
  override fun getName(): String {
    return JAVASCRIPT_NAME
  }

  override fun getLogo(): Icon {
    return AllIcons.Ultimate.PycharmLock
  }


  override fun createPromoPanel(): JPanel {
    return PromoPages.buildWithTryUltimate(
      PromoFeaturePage(
        PythonIcons.Python.Pycharm,
        PluginAdvertiserService.pyCharmProfessional,
        FeaturePromoBundle.message("feature.javascript.description.html",
                                   "https://www.jetbrains.com/help/idea/javascript-specific-guidelines.html"),

        javaScriptPromoFeatureList,
        FeaturePromoBundle.message("free.trial.hint"),
        null,
      ),
      openDownloadLink = createOpenDownloadPageLambda(PromoEventSource.PROJECT_WIZARD, PromoTopic.JavaScript),
      openLearnMore = createOpenLearnMorePageLambda(PromoEventSource.PROJECT_WIZARD, PromoTopic.JavaScript)
    ).withVisualPadding()
  }
}