// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.stats.completion.sender

import com.intellij.ide.ApplicationActivity
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.stats.completion.network.status.WebServiceStatusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.minutes

private fun isSendAllowed(): Boolean {
  return isCompletionLogsSendAllowed() && StatisticsUploadAssistant.isSendAllowed()
}

internal fun isCompletionLogsSendAllowed(): Boolean {
  return ApplicationManager.getApplication().isEAP && System.getProperty("completion.stats.send.logs", "true").toBoolean()
}

private class SenderPreloadingActivity : ApplicationActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute() {
    // do not check right after the start - avoid getting UsageStatisticsPersistenceComponent too early
    delay(5.minutes)
    while (isSendAllowed()) {
      delay(5.minutes)
      runCatching {
        send()
      }.getOrLogException(logger<SenderPreloadingActivity>())
    }
  }

  private suspend fun send() {
    for (status in WebServiceStatusManager.getAllStatuses()) {
      coroutineContext.ensureActive()

      runCatching {
        status.update()
        if (status.isServerOk()) {
          withContext(Dispatchers.IO) {
            service<StatisticSender>().sendStatsData(status.dataServerUrl())
          }
        }
      }.getOrLogException(logger<SenderPreloadingActivity>())
    }
  }
}