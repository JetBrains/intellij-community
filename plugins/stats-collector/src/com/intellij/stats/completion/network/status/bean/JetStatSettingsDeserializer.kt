// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.network.status.bean

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.diagnostic.logger

object JetStatSettingsDeserializer {
  private val GSON by lazy { Gson() }
  private val LOG = logger<JetStatSettingsDeserializer>()

  fun deserialize(json: String): JetStatSettings? {
    try {
      return GSON.fromJson(json, JetStatSettings::class.java)
    }
    catch (e: JsonSyntaxException) {
      LOG.error("Could not parse JetStat settings")
      return null
    }
  }
}