package com.intellij.searchEverywhereMl

import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.searchEverywhereMl.log.MLSE_RECORDER_ID
import com.intellij.util.PlatformUtils
import java.util.concurrent.TimeUnit

private class SearchEverywhereEventLoggerProvider : StatisticsEventLoggerProvider(MLSE_RECORDER_ID, 8, TimeUnit.MINUTES.toMillis(10), 100 * 1024, sendLogsOnIdeClose = true) {
  override fun isRecordEnabled(): Boolean {
    val app = ApplicationManager.getApplication()
    return !app.isUnitTestMode && app.isEAP &&
           StatisticsUploadAssistant.isCollectAllowed() &&
           (ApplicationInfo.getInstance() == null || PlatformUtils.isJetBrainsProduct())
  }

  override fun isSendEnabled(): Boolean {
    return isRecordEnabled() && StatisticsUploadAssistant.isSendAllowed()
  }
}