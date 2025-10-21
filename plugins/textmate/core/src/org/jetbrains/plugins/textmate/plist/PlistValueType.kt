package org.jetbrains.plugins.textmate.plist

enum class PlistValueType {
  STRING, INTEGER, REAL, BOOLEAN, ARRAY, DICT;

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
        else -> {
          error("Unknown type of object: $o")
        }
      }
    }
  }
}
