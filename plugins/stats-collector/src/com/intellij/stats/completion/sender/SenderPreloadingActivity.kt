// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.sender

import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.registry.Registry
import com.intellij.stats.completion.network.status.WebServiceStatusManager
import com.intellij.util.Alarm
import com.intellij.util.Time
import com.intellij.util.containers.forEachLoggingErrors

private fun isSendAllowed(): Boolean {
  return isCompletionLogsSendAllowed() && StatisticsUploadAssistant.isSendAllowed()
}

internal fun isCompletionLogsSendAllowed(): Boolean {
  return ApplicationManager.getApplication().isEAP && Registry.`is`("completion.stats.send.logs")
}

private val LOG = logger<SenderPreloadingActivity>()

@Service
private class StatsCollectorPluginDisposable : Disposable {
  override fun dispose() {
  }
}

internal class SenderPreloadingActivity : PreloadingActivity() {
  private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, service<StatsCollectorPluginDisposable>())
  private val sendInterval = 5 * Time.MINUTE

  override fun preload(indicator: ProgressIndicator) {
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
      WebServiceStatusManager.getAllStatuses().forEachLoggingErrors(LOG) { status ->
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