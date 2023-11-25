// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.promotion

import com.intellij.icons.AllIcons
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.*
import com.intellij.openapi.util.NlsContexts
import javax.swing.Icon
import javax.swing.JComponent
import kotlin.reflect.KClass

internal abstract class ProPromoConfigurable : ConfigurableWithId, Configurable.Promo {
  override fun isModified(): Boolean = false
  override fun apply() = Unit
  override fun getPromoIcon(): Icon = AllIcons.Ultimate.Lock
}

internal abstract class ProPromoConfigurableProvider(private val clazz: KClass<out Configurable>) : ConfigurableProvider() {
  final override fun createConfigurable(): Configurable? {
    return clazz.java.getConstructor().newInstance()
  }
}

private fun featurePage(@NlsContexts.Label title: String, items: List<PromoFeatureListItem>, pluginId: String): JComponent {
  return PromoPages.build(PromoFeaturePage(
    AllIcons.Ultimate.PycharmLock,
    PluginAdvertiserService.pyCharmProfessional,
    title,
    items,
    FeaturePromoBundle.message("free.trial.hint"),
    pluginId
  ))
}

internal class PromoDatabaseConfigurableProvider : ProPromoConfigurableProvider(PromoDatabaseConfigurable::class)
internal class PromoJSConfigurableProvider : ProPromoConfigurableProvider(PromoJSConfigurable::class)
internal class PromoTSConfigurableProvider : ProPromoConfigurableProvider(PromoTSConfigurable::class)
internal class PromoDjangoConfigurableProvider : ProPromoConfigurableProvider(PromoDjangoConfigurable::class)
internal class PromoJupyterConfigurableProvider : ProPromoConfigurableProvider(PromoJupyterConfigurable::class)
internal class PromoRemoteSshConfigurableProvider : ProPromoConfigurableProvider(PromoRemoteSshConfigurable::class)

internal class PromoDatabaseConfigurable : ProPromoConfigurable() {
  override fun getId(): String = "promo.database"
  override fun getDisplayName(): String = FeaturePromoBundle.message("promo.configurable.database")

  override fun createComponent(): JComponent {
    return featurePage(
      FeaturePromoBundle.message("feature.database.description.html", "https://www.jetbrains.com/help/idea/relational-databases.html"),
      listOf(
        PromoFeatureListItem(AllIcons.Nodes.DataTables, FeaturePromoBundle.message("feature.database.create.and.manage")),
        PromoFeatureListItem(AllIcons.Actions.Run_anything, FeaturePromoBundle.message("feature.database.run")),
        PromoFeatureListItem(AllIcons.ToolbarDecorator.Import, FeaturePromoBundle.message("feature.database.export"))
      ),
      "com.intellij.database"
    )
  }
}

private fun javascriptFeaturePage(): JComponent {
  @Suppress("DialogTitleCapitalization")
  return featurePage(
    FeaturePromoBundle.message("feature.javascript.description.html",
                               "https://www.jetbrains.com/help/idea/javascript-specific-guidelines.html"),
    listOf(
      PromoFeatureListItem(AllIcons.Actions.ReformatCode, FeaturePromoBundle.message("feature.javascript.code")),
      PromoFeatureListItem(AllIcons.Actions.SuggestedRefactoringBulb, FeaturePromoBundle.message("feature.javascript.refactor")),
      PromoFeatureListItem(AllIcons.FileTypes.UiForm, FeaturePromoBundle.message("feature.javascript.frameworks"))
    ),
    "JavaScript"
  )
}

internal class PromoJSConfigurable : ProPromoConfigurable() {
  override fun getId(): String = "promo.javascript"
  override fun getDisplayName(): String = FeaturePromoBundle.message("promo.configurable.javascript")
  override fun createComponent(): JComponent = javascriptFeaturePage()
}

internal class PromoTSConfigurable : ProPromoConfigurable() {
  override fun getId(): String = "promo.typescript"
  override fun getDisplayName(): String = FeaturePromoBundle.message("promo.configurable.typescript")
  override fun createComponent(): JComponent = javascriptFeaturePage()
}

internal class PromoDjangoConfigurable : ProPromoConfigurable() {
  override fun getId(): String = "promo.django"
  override fun getDisplayName(): String = FeaturePromoBundle.message("promo.configurable.django")
  override fun createComponent(): JComponent =  djangoFeatures()
}

internal class PromoJupyterConfigurable : ProPromoConfigurable() {
  override fun getId(): String = "promo.jupyter"
  override fun getDisplayName(): String = FeaturePromoBundle.message("promo.configurable.jupyter")
  override fun createComponent(): JComponent =  jupyterFeatures()
}

internal class PromoRemoteSshConfigurable : ProPromoConfigurable() {
  override fun getId(): String = "promo.remoteSsh"
  override fun getDisplayName(): String = FeaturePromoBundle.message("promo.configurable.remoteSsh")
  override fun createComponent(): JComponent = sshFeatures()
}

internal val djangoPromoFeatureList = listOf(
  PromoFeatureListItem(AllIcons.Actions.ReformatCode, FeaturePromoBundle.message("feature.django.code")),
  PromoFeatureListItem(AllIcons.FileTypes.Html, FeaturePromoBundle.message("feature.django.djangoTemplates")),
  PromoFeatureListItem(AllIcons.General.Web, FeaturePromoBundle.message("feature.django.endpoints"))
)

private fun djangoFeatures(): JComponent {
  return featurePage(
    FeaturePromoBundle.message("feature.django.description.html",
                               "https://www.jetbrains.com/help/pycharm/django-support.html"),
    djangoPromoFeatureList,
    "Django"
  )
}

private fun jupyterFeatures(): JComponent {
  return featurePage(
    FeaturePromoBundle.message("feature.jupyter.description",
                               "https://www.jetbrains.com/help/pycharm/jupyter-notebook-support.html"),
    listOf(
      PromoFeatureListItem(AllIcons.Actions.ReformatCode, FeaturePromoBundle.message("feature.jupyter.code")),
      PromoFeatureListItem(AllIcons.Actions.StartDebugger, FeaturePromoBundle.message("feature.jupyter.debugger")),
      PromoFeatureListItem(AllIcons.Nodes.DataSchema, FeaturePromoBundle.message("feature.jupyter.tables")),
      PromoFeatureListItem(AllIcons.Vcs.Branch, FeaturePromoBundle.message("feature.jupyter.vcs"))

    ),
    "Jupyter"
  )
}


private fun sshFeatures(): JComponent {
  return featurePage(
    FeaturePromoBundle.message("feature.remoteSsh.description.html",
                               "https://www.jetbrains.com/help/pycharm/create-ssh-configurations.html"),
    listOf(
      PromoFeatureListItem(AllIcons.Actions.Execute, FeaturePromoBundle.message("feature.remoteSsh.run")),
      PromoFeatureListItem(AllIcons.Nodes.Deploy, FeaturePromoBundle.message("feature.remoteSsh.deploy")),
      PromoFeatureListItem(AllIcons.Toolwindows.SettingSync, FeaturePromoBundle.message("feature.remoteSsh.sync"))
    ),
    "JavaScript"
  )
}
