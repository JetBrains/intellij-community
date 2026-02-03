package com.intellij.mcpserver.impl.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
@PublishedApi
internal val McpServerJson: Json = Json {
  decodeEnumsCaseInsensitive = true
  ignoreUnknownKeys = true
  isLenient = true
  explicitNulls = false
}
