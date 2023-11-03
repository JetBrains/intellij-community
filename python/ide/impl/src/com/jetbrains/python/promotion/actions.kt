// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.promotion

import javax.swing.Icon
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.PromoAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import com.intellij.openapi.util.registry.Registry
import icons.PythonIcons


internal abstract class ProPromoAction(private val pluginId: String?): AnAction(), PromoAction {
  override fun getPromotedProductIcon(): Icon = PythonIcons.Python.Pycharm
  override fun getPromotedProductTitle(): String = PluginAdvertiserService.pyCharmProfessional.name
  override fun getCallToAction(): String = IdeBundle.message("plugin.advertiser.free.trial.action")

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    FUSEventSource.ACTIONS.openDownloadPageAndLog(e.project,
                                                  PluginAdvertiserService.pyCharmProfessional.downloadUrl,
                                                  pluginId?.let { PluginId.getId(it) })
  }
}

internal class PromoJupyterAction : ProPromoAction(pluginId = null)
internal class PromoDjangoAction : ProPromoAction(pluginId = null)
internal class PromoEndpointsAction : ProPromoAction(pluginId = null)
internal class PromoDatabaseAction : ProPromoAction(pluginId = null)
internal class PromoDataFrameAction : ProPromoAction(pluginId = null)
internal class PromoPlotsAction : ProPromoAction(pluginId = null)
internal class PromoJavaScriptAction : ProPromoAction(pluginId = null)
internal class PromoSshRemoteToolsAction : ProPromoAction(pluginId = null)
internal class PromoDockerAction : ProPromoAction(pluginId = null)
internal class PromoAiCodeCompletion : ProPromoAction(pluginId = null)

