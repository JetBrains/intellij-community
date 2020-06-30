// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.network.status

import com.intellij.stats.network.assertNotEDT

open class JetStatServiceStatus : WebServiceStatus() {
  companion object {
    private const val STATUS_URL = "https://www.jetbrains.com/config/features-service-status.json"
  }

  @Volatile
  private var serverStatus = ""
  @Volatile
  private var dataServerUrl = ""

  override val id: String = "JetStat"

  override fun dataServerUrl(): String = dataServerUrl

  override fun isServerOk(): Boolean = serverStatus.equals("ok", ignoreCase = true)

  override fun update() {
    serverStatus = ""
    dataServerUrl = ""

    assertNotEDT()
    val response = getRequestService().get(STATUS_URL)
    if (response != null && response.isOK()) {
      val map = parseServerResponse(response.text) ?: return

      serverStatus = map["status"]?.toString() ?: ""
      dataServerUrl = map["urlForZipBase64Content"]?.toString() ?: ""
    }
  }
}