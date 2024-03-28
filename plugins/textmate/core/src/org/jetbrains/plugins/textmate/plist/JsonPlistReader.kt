package org.jetbrains.plugins.textmate.plist

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class JsonPlistReader : PlistReader {
  @Throws(IOException::class)
  override fun read(inputStream: InputStream): Plist {
    return internalRead(createJsonReader().readValue(InputStreamReader(inputStream, StandardCharsets.UTF_8), Any::class.java))
  }

  companion object {
    @Throws(IOException::class)
    private fun internalRead(root: Any): Plist {
      if (root !is Map<*, *>) {
        throw IOException("Unknown json format. Root element is '$root'")
      }

      return readDict(root as Map<String, Any>).value as Plist
    }

    private fun readDict(map: Map<String, Any>): PListValue {
      val dict = Plist()
      for ((key, value1) in map) {
        val value = readValue(value1)
        if (value != null) {
          dict.setEntry(key, value)
        }
      }

      return PListValue.value(dict, PlistValueType.DICT)
    }

    private fun readValue(value: Any): PListValue? {
      return if (value is Map<*, *>) {
        readDict(value as Map<String, Any>)
      }
      else if (value is ArrayList<*>) {
        readArray(value)
      }
      else {
        readBasicValue(value)
      }
    }

    private fun readArray(list: ArrayList<*>): PListValue {
      val result: MutableList<Any> = ArrayList()
      for (o in list) {
        val value = readValue(o)
        if (value != null) {
          result.add(value)
        }
      }
      return PListValue.value(result, PlistValueType.ARRAY)
    }

    private fun readBasicValue(value: Any): PListValue? {
      if (value is String) {
        return PListValue.value(value, PlistValueType.STRING)
      }
      else if (value is Boolean) {
        return PListValue.value(value, PlistValueType.BOOLEAN)
      }
      else if (value is Int) {
        return PListValue.value(value, PlistValueType.INTEGER)
      }
      else if (value is Double) {
        return PListValue.value(value, PlistValueType.REAL)
      }
      return null
    }

    @JvmStatic
    fun createJsonReader(): ObjectMapper {
      val factory = JsonFactory.builder()
        .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
        .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
        .build()
      return ObjectMapper(factory).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
  }
}
