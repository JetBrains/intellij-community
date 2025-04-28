package org.jetbrains.plugins.textmate.plist

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*

class JsonPlistReader : PlistReaderCore {
  companion object {
    @OptIn(ExperimentalSerializationApi::class)
    val textmateJson: Json by lazy {
      Json {
        isLenient = true
        allowTrailingComma = true
        allowComments = true
        ignoreUnknownKeys = true
      }
    }
  }

  @OptIn(ExperimentalSerializationApi::class)
  override fun read(bytes: ByteArray): Plist {
    val json = textmateJson.decodeFromString(JsonElement.serializer(), bytes.decodeToString()) as JsonObject
    val dict = readDict(json)
    return dict.value as Plist
  }

  private fun readDict(map: Map<String, JsonElement>): PListValue {
    return PListValue.value(Plist(map.mapNotNull { (key, value) ->
      readValue(value)?.let { key to it }
    }.toMap()), PlistValueType.DICT)
  }

  private fun readValue(value: JsonElement): PListValue? {
    return when (value) {
      is JsonObject -> {
        readDict(value)
      }
      is JsonArray -> {
        readArray(value)
      }
      is JsonPrimitive -> {
        readBasicValue(value)
      }
    }
  }

  private fun readArray(list: List<JsonElement>): PListValue {
    return PListValue.value(list.mapNotNull { readValue(it) }, PlistValueType.ARRAY)
  }

  private fun readBasicValue(value: JsonPrimitive): PListValue? {
    if (value.isString) return PListValue.value(value.contentOrNull, PlistValueType.STRING)
    value.booleanOrNull?.let { return PListValue.value(it, PlistValueType.BOOLEAN) }
    value.intOrNull?.let { return PListValue.value(it, PlistValueType.INTEGER) }
    value.doubleOrNull?.let { return PListValue.value(it, PlistValueType.REAL) }
    return null
  }
}
