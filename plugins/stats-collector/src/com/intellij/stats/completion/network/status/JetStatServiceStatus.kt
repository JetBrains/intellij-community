// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.network.status

import com.intellij.openapi.components.service
import com.intellij.stats.completion.network.assertNotEDT
import com.intellij.stats.completion.network.service.RequestService
import com.intellij.stats.completion.network.status.bean.JetStatSettingsDeserializer

open class JetStatServiceStatus : WebServiceStatus {
  companion object {
    private const val STATUS_URL = "https://www.jetbrains.com/config/features-service-status.json"
  }

  @Volatile
  private var serverStatus = ""
  @Volatile
  private var dataServerUrl = ""

  private val requestService: RequestService = service()

  override val id: String = "JetStat"

  override fun dataServerUrl(): String = dataServerUrl

  override fun isServerOk(): Boolean = serverStatus.equals("ok", ignoreCase = true)

  override fun update() {
    serverStatus = ""
    dataServerUrl = ""

    assertNotEDT()
    val response = requestService.get(STATUS_URL)
    if (response != null && response.isOK()) {
      val settings = JetStatSettingsDeserializer.deserialize(response.text) ?: return

      serverStatus = settings.status
      dataServerUrl = settings.urlForZipBase64Content
    }
  }
}