// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion.communityToUnified

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.updateSettings.impl.PlatformUpdateDialog
import com.intellij.openapi.updateSettings.impl.PlatformUpdates
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.pycharm.community.ide.impl.promotion.communityToUnified.statistics.PyCommunityUnifiedPromoFusCollector
import com.intellij.util.PlatformUtils
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PyCommunityToUnifiedShowPromoActivity : ProjectActivity {

  override suspend fun execute(project: Project) {
    showPromoIfNeeded(project)
  }

  suspend fun showPromoIfNeeded(project: Project) {
    if (project.isDisposed || application.isHeadlessEnvironment || !PlatformUtils.isPyCharmCommunity()) return

    val promoService = service<PyCommunityToUnifiedPromoService>()
    val update = promoService.getAvailableUpdate()
    if (update == null) return

    if (promoService.shouldShowTooltip()) {
      Helper.showTooltip(project)
    }
    else if (promoService.shouldShowModal()) {
      Helper.showModalPromo(project)
    }
  }

  object Helper {
    suspend fun showTooltip(project: Project) {
      withContext(Dispatchers.EDT) {
        PyCommunityToUnifiedTooltip.showTooltip(project)
      }
    }

    suspend fun showModalPromo(project: Project) {
      withContext(Dispatchers.EDT) {
        val started = System.currentTimeMillis()
        PyCommunityUnifiedPromoFusCollector.PromoModalShown.log()
        val result = PyCommunityToUnifiedPromoDialog(project).showAndGet()
        val duration = System.currentTimeMillis() - started
        if (!result) {
          PyCommunityUnifiedPromoFusCollector.PromoModalClosed.log(PyCommunityUnifiedPromoFusCollector.PromoModalCloseReason.DISMISSED,
                                                                   duration)
          service<PyCommunityToUnifiedPromoService>().onRemindMeLaterClicked()
          PyCharmCommunityToUnifiedScheduleService.getInstance().scheduleFallbackModal()
        }
        else {
          PyCommunityUnifiedPromoFusCollector.PromoModalClosed.log(PyCommunityUnifiedPromoFusCollector.PromoModalCloseReason.UPDATE_NOW,
                                                                   duration)
          launchUpdateDialog(project)
        }
      }
    }

    fun launchUpdateDialog(project: Project?) {
      val promoService = service<PyCommunityToUnifiedPromoService>()
      promoService.serviceScope.launch {
        val update = promoService.getAvailableUpdate()
        if (update != null) {
          showModalUpdateDialog(project, update)
        }
        else {
          LOG.warn("Update dialog was launched but no update was found")
        }
      }
    }

    private suspend fun showModalUpdateDialog(project: Project?, update: PlatformUpdates.Loaded) {
      withContext(Dispatchers.EDT) {
        val started = System.currentTimeMillis()
        PyCommunityUnifiedPromoFusCollector.UpdateDialogShown.log()
        val result = PlatformUpdateDialog(project, update, true, emptyList(), emptyList()).showAndGet()
        val duration = System.currentTimeMillis() - started
        val promoService = service<PyCommunityToUnifiedPromoService>()
        if (!result) {
          val buildNum = update.newBuild.number.asStringWithoutProductCode()
          // ignore this update was clicked
          if (UpdateSettings.getInstance().ignoredBuildNumbers.contains(buildNum)) {
            LOG.info("Update declined for: $buildNum")
            promoService.setUpdateDeclined()
            PyCommunityUnifiedPromoFusCollector.UpdateDialogClosed.log(PyCommunityUnifiedPromoFusCollector.UpdateDialogCloseReason.IGNORE,
                                                                       duration)
          }
          else {
            LOG.info("Remind me later clicked for: $buildNum")
            promoService.onRemindMeLaterClicked()
            PyCharmCommunityToUnifiedScheduleService.getInstance().scheduleFallbackModal()
            PyCommunityUnifiedPromoFusCollector.UpdateDialogClosed.log(PyCommunityUnifiedPromoFusCollector.UpdateDialogCloseReason.REMIND_LATER,
                                                                       duration)
          }
        }
        else {
          PyCommunityUnifiedPromoFusCollector.UpdateDialogClosed.log(PyCommunityUnifiedPromoFusCollector.UpdateDialogCloseReason.UPDATE_NOW,
                                                                     duration)
        }
      }
    }
  }

  companion object {
    val LOG: Logger = logger<PyCommunityToUnifiedShowPromoActivity>()

  }
}