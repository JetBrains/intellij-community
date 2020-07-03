// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.network.status.bean

import com.google.gson.*
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.logger
import java.lang.reflect.Type

object AnalyticsPlatformSettingsDeserializer {
  private val GSON by lazy {
    GsonBuilder()
      .registerTypeAdapter(Language::class.java, object : JsonDeserializer<Language> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Language {
          if (!json.isJsonPrimitive) throw JsonSyntaxException("No string field \"language\"")
          val value = json.asString
          if (value == "ANY") return Language.ANY
          return Language.findLanguageByID(value) ?: throw JsonSyntaxException("No language with id: $value")
        }
      })
      .registerTypeAdapter(MajorVersion::class.java, object : JsonDeserializer<MajorVersion> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): MajorVersion {
          if (!json.isJsonPrimitive) throw JsonSyntaxException("No string field for major version")
          return MajorVersion(json.asString)
        }
      })
      .create()
  }
  private val LOG = logger<AnalyticsPlatformSettingsDeserializer>()

  fun deserialize(json: String): AnalyticsPlatformSettings? {
    try {
      return GSON.fromJson(json, AnalyticsPlatformSettings::class.java)
    }
    catch (e: JsonSyntaxException) {
      LOG.warn("Could not parse Analytics Platform settings", e)
      return null
    }
  }
}