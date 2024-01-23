// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion

import com.intellij.icons.AllIcons
import com.intellij.ide.wizard.withVisualPadding
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FeaturePromoBundle
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PromoFeaturePage
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PromoPages
import com.intellij.openapi.util.NlsSafe
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.jetbrains.python.newProject.PyNewProjectSettings
import com.jetbrains.python.newProject.PythonProjectGenerator
import com.jetbrains.python.newProject.PythonPromoProjectGenerator
import com.jetbrains.python.icons.PythonIcons
import javax.swing.Icon
import javax.swing.JPanel

@NlsSafe
private const val DJANGO_NAME = "Django"

internal class DjangoPromoProjectGenerator : PythonProjectGenerator<PyNewProjectSettings>(), PythonPromoProjectGenerator {
  override fun getName(): String {
    return DJANGO_NAME
  }

  override fun getLogo(): Icon {
    return AllIcons.Ultimate.PycharmLock
  }

  override fun createPromoPanel(): JPanel {
    return PromoPages.build(
      PromoFeaturePage(
        PythonIcons.Python.Pycharm,
        PluginAdvertiserService.pyCharmProfessional,
        PyCharmCommunityCustomizationBundle.message("feature.django.description.html",
                                                    "https://www.jetbrains.com/help/pycharm/django-support.html"
        ),
        djangoPromoFeatureList,
        FeaturePromoBundle.message("free.trial.hint"),
        null,
      ),
      openDownloadLink = createOpenDownloadPageLambda(PromoEventSource.PROJECT_WIZARD, PromoTopic.Django),
      openLearnMore = createOpenLearnMorePageLambda(PromoEventSource.PROJECT_WIZARD, PromoTopic.Django)
    ).withVisualPadding()
  }
}