// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.PromoAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FeaturePromoBundle
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.tryUltimate
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.python.icons.PythonIcons
import javax.swing.Icon


internal abstract class ProPromoAction(private val topic: PromoTopic): AnAction(), PromoAction {
  override fun getPromotedProductIcon(): Icon? = PythonIcons.Python.Pycharm
  override fun getCallToAction(): String {
    return IdeBundle.message("plugin.advertiser.product.call.to.action",
                             PluginAdvertiserService.pyCharmProfessional.name,
                             IdeBundle.message("plugin.advertiser.free.trial.action"))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    tryUltimate(
      null,
      PluginAdvertiserService.pyCharmProfessional,
      e.project,
      null,
      createOpenDownloadPageLambda(PromoEventSource.GO_TO_ACTION, topic)
    ) 
  }
}

internal class PromoEndpointsAction : ProPromoAction(PromoTopic.Endpoints)
internal class PromoDataFrameAction : ProPromoAction(PromoTopic.Dataframe)
internal class PromoPlotsAction : ProPromoAction(PromoTopic.Plots)
internal class PromoDockerAction : ProPromoAction(PromoTopic.Docker)
internal class PromoAiCodeCompletion : ProPromoAction(PromoTopic.AiCodeCompletion) {

  override fun getPromotedProductIcon(): Icon? = null

  override fun getCallToAction() = FeaturePromoBundle.message("promo.ai.assistant.message")

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    activateAIAssistantToolwindow(project)
  }
}

fun activateAIAssistantToolwindow(project: Project) {
  val toolWindowManager = ToolWindowManager.getInstance(project)
  val aiAssistantToolWindow = toolWindowManager.getToolWindow("AIAssistant") ?: return
  aiAssistantToolWindow.show()
  aiAssistantToolWindow.contentManager.selectedContent?.component?.requestFocus()
}

