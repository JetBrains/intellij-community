// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.PromoAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import com.jetbrains.python.icons.PythonIcons
import javax.swing.Icon


internal abstract class ProPromoAction(private val topic: PromoTopic): AnAction(), PromoAction {
  override fun getPromotedProductIcon(): Icon = PythonIcons.Python.Pycharm
  override fun getPromotedProductTitle(): String = PluginAdvertiserService.pyCharmProfessional.name
  override fun getCallToAction(): String = IdeBundle.message("plugin.advertiser.free.trial.action")

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    createOpenDownloadPageLambda(PromoEventSource.GO_TO_ACTION, topic).invoke()
  }
}

internal class PromoEndpointsAction : ProPromoAction(PromoTopic.Endpoints)
internal class PromoDataFrameAction : ProPromoAction(PromoTopic.Dataframe)
internal class PromoPlotsAction : ProPromoAction(PromoTopic.Plots)
internal class PromoDockerAction : ProPromoAction(PromoTopic.Docker)
internal class PromoAiCodeCompletion : ProPromoAction(PromoTopic.AiCodeCompletion)

