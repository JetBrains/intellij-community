// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion

import com.intellij.icons.AllIcons
import com.intellij.llmInstaller.LLMIcons
import com.intellij.llmInstaller.LLMInstallerBundle
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.*
import com.intellij.openapi.util.NlsContexts
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.jetbrains.python.icons.PythonIcons
import javax.swing.JComponent

private fun featurePage(@NlsContexts.Label title: String, items: List<PromoFeatureListItem>,
                        source: PromoEventSource,
                        topic: PromoTopic): JComponent {
  val page = PromoFeaturePage(
    productIcon = PythonIcons.Python.Pycharm,
    suggestedIde = PluginAdvertiserService.pyCharmProfessional,
    descriptionHtml = title,
    features = items,
    trialLabel = FeaturePromoBundle.message("free.trial.hint"),
    pluginId = null
  )
  return PromoPages.buildWithTryUltimate(
    page = page,
    openLearnMore = createOpenLearnMorePageLambda(source, topic),
    openDownloadLink = createOpenDownloadPageLambda(source, topic),
  )
}

private fun aiAssistantFeaturePage(@NlsContexts.Label title: String, items: List<PromoFeatureListItem>,
                                   source: PromoEventSource): JComponent {
  val page = object: PromoFeaturePage(
    productIcon = PythonIcons.Python.Pycharm,
    suggestedIde = PluginAdvertiserService.pyCharmProfessional,
    descriptionHtml = title,
    features = items,
    trialLabel = "",
    pluginId = null
  ) {
    override fun getButtonOpenPromotionText() = PyCharmCommunityCustomizationBundle.message("feature.ai.assistant.open.toolwindow")

    override fun getTitle() = PyCharmCommunityCustomizationBundle.message("feature.ai.assistant.title")
  }
  return PromoPages.build(
    page = page,
    openLearnMore = createOpenLearnMorePageLambda(source, PromoTopic.AiCodeCompletion),
    openDownloadLink = { dialog ->
      ProjectManager.getInstance().openProjects.forEach {
        activateAIAssistantToolwindow(it)
      }
      dialog?.close(DialogWrapper.CLOSE_EXIT_CODE)
    }
  )
}

internal fun databaseFeatures(source: PromoEventSource): JComponent = featurePage(
  FeaturePromoBundle.message("feature.database.description.html", "https://www.jetbrains.com/help/pycharm/relational-databases.html"),
  listOf(
    PromoFeatureListItem(AllIcons.Actions.ReformatCode, FeaturePromoBundle.message("feature.database.code")),
    PromoFeatureListItem(AllIcons.Nodes.DataTables, FeaturePromoBundle.message("feature.database.create.and.manage")),
    PromoFeatureListItem(AllIcons.Actions.Run_anything, FeaturePromoBundle.message("feature.database.run")),
    PromoFeatureListItem(AllIcons.ToolbarDecorator.Import, FeaturePromoBundle.message("feature.database.export"))
  ),
  source,
  PromoTopic.Database
)

internal fun javascriptFeatures(source: PromoEventSource, promoTopic: PromoTopic): JComponent {
  @Suppress("DialogTitleCapitalization")
  return featurePage(
    FeaturePromoBundle.message("feature.javascript.description.html",
                               "https://www.jetbrains.com/help/pycharm/javascript-specific-guidelines.html"),
    javaScriptPromoFeatureList,
    source,
    promoTopic
  )
}

internal fun djangoFeatures(source: PromoEventSource): JComponent {
  return featurePage(
    PyCharmCommunityCustomizationBundle.message("feature.django.description.html",
                                                "https://www.jetbrains.com/help/pycharm/django-support.html"),
    djangoPromoFeatureList,
    source,
    PromoTopic.Django
  )
}

internal fun aiAssistantFeatures(source: PromoEventSource): JComponent {
  return aiAssistantFeaturePage(
    PyCharmCommunityCustomizationBundle.message("feature.ai.assistant.description.html", "https://www.jetbrains.com/ai"),
    aiAssistantFeatureList,
    source
  )
}

internal fun jupyterFeatures(source: PromoEventSource): JComponent {
  return featurePage(
    PyCharmCommunityCustomizationBundle.message("feature.jupyter.description.html",
                                                "https://www.jetbrains.com/help/pycharm/jupyter-notebook-support.html"),
    listOf(
      PromoFeatureListItem(AllIcons.Actions.ReformatCode, PyCharmCommunityCustomizationBundle.message("feature.jupyter.code")),
      PromoFeatureListItem(AllIcons.Actions.StartDebugger, PyCharmCommunityCustomizationBundle.message("feature.jupyter.debugger")),
      PromoFeatureListItem(AllIcons.Nodes.DataSchema, PyCharmCommunityCustomizationBundle.message("feature.jupyter.tables")),
      PromoFeatureListItem(AllIcons.Vcs.Branch, PyCharmCommunityCustomizationBundle.message("feature.jupyter.vcs"))
    ),
    source,
    PromoTopic.Jupyter
  )
}

internal fun sshFeatures(source: PromoEventSource): JComponent {
  return featurePage(
    PyCharmCommunityCustomizationBundle.message("feature.remoteSsh.description.html",
                                                "https://www.jetbrains.com/help/pycharm/create-ssh-configurations.html"),
    listOf(
      PromoFeatureListItem(AllIcons.Actions.Execute, PyCharmCommunityCustomizationBundle.message("feature.remoteSsh.run")),
      PromoFeatureListItem(AllIcons.Nodes.Deploy, PyCharmCommunityCustomizationBundle.message("feature.remoteSsh.deploy")),
      PromoFeatureListItem(AllIcons.Toolwindows.SettingSync, PyCharmCommunityCustomizationBundle.message("feature.remoteSsh.sync"))
    ),
    source,
    PromoTopic.RemoteSSH
  )
}

internal val djangoPromoFeatureList = listOf(
  PromoFeatureListItem(AllIcons.Actions.ReformatCode, PyCharmCommunityCustomizationBundle.message("feature.django.code")),
  PromoFeatureListItem(AllIcons.FileTypes.Html, PyCharmCommunityCustomizationBundle.message("feature.django.djangoTemplates")),
  PromoFeatureListItem(AllIcons.General.Web, PyCharmCommunityCustomizationBundle.message("feature.django.endpoints"))
)

internal val aiAssistantFeatureList = listOf(
  PromoFeatureListItem(LLMIcons.Login.FeatureContext, LLMInstallerBundle.message("panel.activate.suggestion.enjoy.ai.features")),
  PromoFeatureListItem(LLMIcons.Login.FeatureComment, LLMInstallerBundle.message("panel.activate.suggestion.ask.anything")),
  PromoFeatureListItem(LLMIcons.Login.FeatureCodeBlock, LLMInstallerBundle.message("panel.activate.suggestion.generate.code")),
  PromoFeatureListItem(LLMIcons.Login.FeatureAnswer, LLMInstallerBundle.message("panel.activate.suggestion.explain.errors")),
  PromoFeatureListItem(LLMIcons.Login.FeatureDocs, LLMInstallerBundle.message("panel.activate.suggestion.generate.commit.messages"))

)

internal val javaScriptPromoFeatureList = listOf(
  PromoFeatureListItem(AllIcons.Actions.ReformatCode, FeaturePromoBundle.message("feature.javascript.code")),
  PromoFeatureListItem(AllIcons.Actions.SuggestedRefactoringBulb, FeaturePromoBundle.message("feature.javascript.refactor")),
  PromoFeatureListItem(AllIcons.FileTypes.UiForm, FeaturePromoBundle.message("feature.javascript.frameworks"))
)