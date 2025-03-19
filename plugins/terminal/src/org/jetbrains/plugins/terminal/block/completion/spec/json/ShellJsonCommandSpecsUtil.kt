// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.json

import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.serialization.json.Json
import java.io.IOException

internal object ShellJsonCommandSpecsUtil {
  private val json: Json by lazy {
    Json { ignoreUnknownKeys = true }
  }

  inline fun <reified T> loadAndParseJson(path: String, classLoader: ClassLoader): T? {
    val url = classLoader.getResource(path)
    if (url == null) {
      thisLogger().warn("Failed to find resource for path: $path with classLoader: $classLoader")
      return null
    }
    return try {
      val resultJson = url.readText()
      json.decodeFromString<T>(resultJson)
    }
    catch (ex: IOException) {
      thisLogger().warn("Failed to load resource by URL: $url", ex)
      null
    }
    catch (t: Throwable) {
      thisLogger().warn("Failed to parse resource loaded from URL: $url", t)
      null
    }
  }
}