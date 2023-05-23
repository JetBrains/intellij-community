// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.stats.completion.sender

import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.stats.completion.network.status.WebServiceStatusManager
import com.intellij.util.Alarm
import com.intellij.util.Time
import com.intellij.util.containers.forEachLoggingErrors

private fun isSendAllowed(): Boolean {
  return isCompletionLogsSendAllowed() && StatisticsUploadAssistant.isSendAllowed()
}

internal fun isCompletionLogsSendAllowed(): Boolean {
  return ApplicationManager.getApplication().isEAP && java.lang.Boolean.parseBoolean(System.getProperty("completion.stats.send.logs", "true"))
}

@Service
private class StatsCollectorPluginDisposable : Disposable {
  override fun dispose() {
  }
}

private class SenderPreloadingActivity : PreloadingActivity() {
  private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, service<StatsCollectorPluginDisposable>())
  private val sendInterval = 5 * Time.MINUTE

  override suspend fun execute() {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      return
    }

    if (isSendAllowed()) {
      alarm.addRequest({ send() }, sendInterval)
    }
  }

  private fun send() {
    if (!isSendAllowed()) {
      return
    }

    try {
      WebServiceStatusManager.getAllStatuses().forEachLoggingErrors(logger<SenderPreloadingActivity>()) { status ->
        status.update()
        if (status.isServerOk()) {
          service<StatisticSender>().sendStatsData(status.dataServerUrl())
        }
      }
    }
    finally {
      alarm.addRequest({ send() }, sendInterval)
    }
  }
}