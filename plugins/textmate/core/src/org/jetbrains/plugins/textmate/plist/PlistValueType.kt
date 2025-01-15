package org.jetbrains.plugins.textmate.plist

import java.util.*

enum class PlistValueType {
  STRING, INTEGER, REAL, BOOLEAN, DATE, ARRAY, DICT;

  companion object {
    fun fromObject(o: Any?): PlistValueType {
      when (o) {
        is String -> {
          return STRING
        }
        is Int -> {
          return INTEGER
        }
        is Plist -> {
          return DICT
        }
        is List<*> -> {
          return ARRAY
        }
        is Double -> {
          return REAL
        }
        is Boolean -> {
          return BOOLEAN
        }
        is Date -> {
          return DATE
        }
        else -> {
          error("Unknown type of object: $o")
        }
      }
    }
  }
}
