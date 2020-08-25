// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.network.status.bean

import com.google.gson.*
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Version
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
      .registerTypeAdapter(Version::class.java, object : JsonDeserializer<Version> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Version {
          if (!json.isJsonPrimitive) throw JsonSyntaxException("No string field for major version")
          val value = json.asString
          return Version.parseVersion(value) ?:  throw JsonSyntaxException("Couldn't parse major version: $value")
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
      LOG.error("Could not parse Analytics Platform settings", e)
      return null
    }
  }
}