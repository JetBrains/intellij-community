package org.jetbrains.plugins.textmate.plist

import java.util.*

data class PListValue(val value: Any?, val type: PlistValueType) {
  val plist: Plist
    get() = when {
      value == null -> Plist.EMPTY_PLIST
      type == PlistValueType.DICT -> this.value as Plist
      else -> Plist.EMPTY_PLIST
    }

  val array: List<PListValue>
    get() = when {
      value == null -> emptyList()
      type == PlistValueType.ARRAY -> this.value as List<PListValue>
      else -> emptyList()
    }

  val stringArray: List<String?>
    get() {
      return array.map(PListValue::string)
    }

  val string: String?
    get() = when {
      value == null -> null
      type == PlistValueType.STRING -> this.value as String?
      else -> value.toString()
    }

  companion object {
    fun value(value: Any?, type: PlistValueType): PListValue {
      return PListValue(value, type)
    }

    fun string(value: String?): PListValue {
      return value(value, PlistValueType.STRING)
    }

    fun bool(value: Boolean?): PListValue {
      return value(value, PlistValueType.BOOLEAN)
    }

    fun integer(value: Int?): PListValue {
      return value(value, PlistValueType.INTEGER)
    }

    fun real(value: Double?): PListValue {
      return value(value, PlistValueType.REAL)
    }

    fun date(value: Date?): PListValue {
      return value(value, PlistValueType.DATE)
    }

    fun array(value: List<PListValue?>?): PListValue {
      return value(value, PlistValueType.ARRAY)
    }

    fun array(vararg value: PListValue?): PListValue {
      return value(value.toList(), PlistValueType.ARRAY)
    }

    fun dict(value: Plist?): PListValue {
      return value(value, PlistValueType.DICT)
    }
  }
}
