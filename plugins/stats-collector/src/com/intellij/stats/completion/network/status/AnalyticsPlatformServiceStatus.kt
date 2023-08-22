// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.network.status

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.stats.completion.network.assertNotEDT
import com.intellij.stats.completion.network.service.RequestService
import com.intellij.stats.completion.network.status.bean.AnalyticsPlatformSettingsDeserializer

class AnalyticsPlatformServiceStatus(private val statusUrl: String) : WebServiceStatus {
  companion object {
    private val LOG = logger<AnalyticsPlatformServiceStatus>()
    private fun productCode(): String = ApplicationInfo.getInstance().build.productCode

    fun withDefaultUrl(): AnalyticsPlatformServiceStatus =
      AnalyticsPlatformServiceStatus("https://resources.jetbrains.com/storage/ap/mlcc/config/v1/${productCode()}.json")
  }
  @Volatile
  private var isServerOk = false
  @Volatile
  private var dataServerUrl = ""

  private val requestService: RequestService = service()

  override val id: String = "AnalyticsPlatform"

  override fun isServerOk(): Boolean = isServerOk

  override fun dataServerUrl(): String = dataServerUrl

  override fun update() {
    isServerOk = false
    dataServerUrl = ""

    assertNotEDT()
    val response = requestService.get(statusUrl)
    if (response != null && response.isOK()) {
      val settings = AnalyticsPlatformSettingsDeserializer.deserialize(response.text) ?: return

      val satisfyingEndpoints = settings.versions.filter { it.satisfies() && it.endpoint != null }
      if (satisfyingEndpoints.isEmpty()) {
        LOG.debug("Analytics Platform completion web service status. No satisfying endpoints.")
        return
      }
      if (satisfyingEndpoints.size > 1) {
        LOG.error("Analytics Platform completion web service status. More than one satisfying endpoints. First one will be used.")
      }
      val endpointSettings = satisfyingEndpoints.first()
      isServerOk = true
      dataServerUrl = endpointSettings.endpoint!!
    }
  }
}