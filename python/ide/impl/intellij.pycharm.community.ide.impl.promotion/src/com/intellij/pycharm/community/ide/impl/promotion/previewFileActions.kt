// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.PromoAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.tryUltimate
import com.jetbrains.python.icons.PythonIcons
import javax.swing.Icon

internal abstract class PreviewFilePromoAction(private val topic: PromoTopic) : AnAction(), PromoAction {
  override fun getPromotedProductIcon(): Icon = PythonIcons.Python.Pycharm
  override fun getCallToAction(): String {
    return IdeBundle.message("plugin.advertiser.product.call.to.action",
                             PluginAdvertiserService.pyCharmProfessional.name,
                             IdeBundle.message("plugin.advertiser.free.trial.action"))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    tryUltimate(null,
                PluginAdvertiserService.pyCharmProfessional,
                e.project,
                null,
                createOpenDownloadPageLambda(PromoEventSource.FILE_PREVIEW, topic))
  }
}

internal class PreviewJupyterNotebookFile : PreviewFilePromoAction(PromoTopic.Jupyter)