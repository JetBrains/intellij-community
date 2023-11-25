// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.promotion

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.intellij.icons.AllIcons
import com.intellij.ide.util.projectWizard.*
import com.intellij.ide.wizard.withVisualPadding
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.*
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.PythonModuleTypeBase
import com.jetbrains.python.newProject.PyNewProjectSettings
import com.jetbrains.python.newProject.PythonProjectGenerator
import com.jetbrains.python.newProject.PythonPromoProjectGenerator
import icons.PythonIcons
import javax.swing.Icon
import javax.swing.JPanel

@NlsSafe
private const val DJANGO_NAME = "Django"
private const val SPRING_PLUGIN_ID = "com.intellij.spring"


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
        FeaturePromoBundle.message("feature.django.description.html",
                                   "https://www.jetbrains.com/help/pycharm/django-support.html"
        ),
        djangoPromoFeatureList,
        FeaturePromoBundle.message("free.trial.hint"),
        SPRING_PLUGIN_ID
      ),
      FUSEventSource.NEW_PROJECT_WIZARD
    ).withVisualPadding()
  }
}


internal class DjangoPromoModuleBuilder : ModuleBuilder(), PromoModuleBuilder {
  override fun isAvailable(): Boolean = Registry.`is`("idea.ultimate.features.hints.enabled")

  override fun getModuleType(): ModuleType<*> = PythonModuleTypeBase.getInstance()
  override fun getWeight(): Int = JVM_WEIGHT

  override fun getBuilderId(): String = "promo-spring"
  override fun getNodeIcon(): Icon = PythonIcons.Django.DjangoLogo
  override fun getPresentableName(): String = DJANGO_NAME
  override fun getDescription(): String = FeaturePromoBundle.message("promo.configurable.django")

  override fun modifyProjectTypeStep(settingsStep: SettingsStep): ModuleWizardStep? = null

  override fun getCustomOptionsStep(context: WizardContext?, parentDisposable: Disposable?): ModuleWizardStep {
    TODO()
  }
}