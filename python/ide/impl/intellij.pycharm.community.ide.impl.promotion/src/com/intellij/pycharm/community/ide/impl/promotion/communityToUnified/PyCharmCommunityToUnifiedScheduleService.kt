// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion.communityToUnified

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException

@Service(Service.Level.APP)
@ApiStatus.Internal
class PyCharmCommunityToUnifiedScheduleService(val serviceScope: CoroutineScope) {
  companion object {
    val LOG: Logger = logger<PyCharmCommunityToUnifiedScheduleService>()
    fun getInstance(): PyCharmCommunityToUnifiedScheduleService = service<PyCharmCommunityToUnifiedScheduleService>()
  }

  private var remindLaterJob: Job? = null

  fun scheduleFallbackModal() {
    val promoService = service<PyCommunityToUnifiedPromoService>()
    // cancel previous scheduled task if any
    remindLaterJob?.cancel()
    val delayMs = promoService.getPromoIntervalMillis()
    remindLaterJob = serviceScope.launch(Dispatchers.Default) {
      try {
        delay(delayMs)
        // Only proceed if conditions still require showing modal
        if (!promoService.shouldShowModal()) return@launch
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: run {
          LOG.info("Skipping promo modal fallback: no open projects")
          return@launch
        }
        PyCommunityToUnifiedShowPromoActivity.Helper.showModalPromo(project)
      }
      catch (t: Throwable) {
        if (t is CancellationException) throw t
        LOG.warn("Error in promo modal fallback task", t)
      }
    }
  }
}