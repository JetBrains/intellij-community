// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.network.status

import com.google.gson.internal.LinkedTreeMap
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.stats.network.assertNotEDT

class AnalyticsPlatformServiceStatus : WebServiceStatus() {
  private val statusUrl = "https://resources.jetbrains.com/storage/ap/mlcc/config/v1/${productCode()}.json"

  @Volatile
  private var isServerOk = false
  @Volatile
  private var dataServerUrl = ""

  override val id: String = "AnalyticsPlatform"

  override fun isServerOk(): Boolean = isServerOk

  override fun dataServerUrl(): String = dataServerUrl

  override fun update() {
    isServerOk = false
    dataServerUrl = ""

    assertNotEDT()
    val response = getRequestService().get(statusUrl)
    if (response != null && response.isOK()) {
      val map = parseServerResponse(response.text) ?: return

      //TODO: filter endpoints by build numbers
      for (versionRaw in map["versions"] as List<*>) {
        val version = versionRaw as LinkedTreeMap<*, *>
        if (true) {
          isServerOk = true
          dataServerUrl = version["endpoint"]?.toString() ?: ""
          break
        }
      }
    }
  }

  private fun productCode(): String = ApplicationInfo.getInstance().build.productCode
}