// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.stats.completion.sender

import com.intellij.ide.ApplicationActivity
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.util.registry.Registry
import com.intellij.stats.completion.network.service.RequestService
import com.intellij.stats.completion.network.status.bean.AnalyticsPlatformSettingsDeserializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes

internal fun isCompletionLogsSendAllowed(): Boolean =
  ApplicationManager.getApplication().isEAP && System.getProperty("completion.stats.send.logs", "true").toBoolean()

private const val USE_ANALYTICS_PLATFORM_KEY = "completion.stats.analytics.platform.send"
private const val ANALYTICS_PLATFORM_URL_KEY = "completion.stats.analytics.platform.url"

private val LOG = logger<SenderPreloadingActivity>()

private class SenderPreloadingActivity : ApplicationActivity {
  private val statusUrl: String

  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment || !Registry.`is`(USE_ANALYTICS_PLATFORM_KEY, false)) {
      throw ExtensionNotApplicableException.create()
    }
    statusUrl = Registry.get(ANALYTICS_PLATFORM_URL_KEY).takeIf { it.isChangedFromDefault() }?.asString()
                ?: "https://resources.jetbrains.com/storage/ap/mlcc/config/v1/${ApplicationInfo.getInstance().build.productCode}.json"
  }

  override suspend fun execute() {
    // do not check right after the start - avoid getting UsageStatisticsPersistenceComponent too early
    delay(5.minutes)
    while (isCompletionLogsSendAllowed() && StatisticsUploadAssistant.isSendAllowed()) {
      delay(5.minutes)
      withContext(Dispatchers.IO) {
        runCatching {
          updateAndGetUrl()?.let { url ->
            service<StatisticSender>().sendStatsData(url)
          }
        }.onFailure {
          LOG.error(it)
        }
      }
    }
  }

  private fun updateAndGetUrl(): String? {
    val response = service<RequestService>().get(statusUrl)
    if (response == null || !response.isOK()) return null

    val settings = AnalyticsPlatformSettingsDeserializer.deserialize(response.text) ?: return null

    val satisfyingEndpoints = settings.versions.filter { it.satisfies() && it.endpoint != null }
    if (satisfyingEndpoints.isEmpty()) {
      LOG.debug("Analytics Platform completion web service status. No satisfying endpoints.")
      return null
    }
    if (satisfyingEndpoints.size > 1) {
      LOG.error("Analytics Platform completion web service status. More than one satisfying endpoints. First one will be used.")
    }
    return satisfyingEndpoints.first().endpoint!!
  }
}
