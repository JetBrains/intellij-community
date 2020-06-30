// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.network.status

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.internal.LinkedTreeMap
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.stats.network.service.RequestService

abstract class WebServiceStatus {
  companion object {
    private val GSON by lazy { Gson() }
    private val LOG = logger<WebServiceStatus>()
  }

  abstract val id: String

  abstract fun isServerOk(): Boolean
  abstract fun dataServerUrl(): String

  abstract fun update()

  protected open fun getRequestService(): RequestService = service()

  protected fun parseServerResponse(responseText: String): LinkedTreeMap<*, *>? {
    try {
      return GSON.fromJson(responseText, LinkedTreeMap::class.java)
    }
    catch (e: JsonSyntaxException) {
      LOG.warn("Could not parse server response for service $id")
      return null
    }
  }
}