package org.jetbrains.plugins.textmate.plist

import org.jetbrains.plugins.textmate.plist.PlistValueType.Companion.fromObject
import java.text.SimpleDateFormat

data class Plist(private val myMap: Map<String, PListValue>) {
  fun getPlistValue(key: String): PListValue? {
    return myMap[key]
  }

  fun getPlistValue(key: String, defValue: Any): PListValue {
    return myMap[key] ?: PListValue(defValue, fromObject(defValue))
  }

  fun contains(key: String): Boolean {
    return myMap.containsKey(key)
  }

  fun entries(): Set<Map.Entry<String, PListValue>> {
    return myMap.entries
  }

  companion object {
    @JvmField
    val EMPTY_PLIST: Plist = Plist(emptyMap())

    fun dateFormatter(): SimpleDateFormat {
      return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    }
  }
}
