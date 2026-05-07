package org.jetbrains.plugins.textmate.plist

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper

class YamlPlistReader : PlistReaderCore {
  private val textmateYaml: YAMLMapper by lazy {
    YAMLMapper()
  }

  override fun read(bytes: ByteArray): Plist {
    val yaml = textmateYaml.readTree(bytes)
    if (yaml == null || yaml.isNull || yaml.isMissingNode) {
      return Plist.EMPTY_PLIST
    }

    require(yaml is ObjectNode) {
      "Unknown yaml format. Root element is '${yaml.nodeType}'"
    }

    return readDict(yaml).plist
  }

  private fun readDict(map: ObjectNode): PListValue {
    return PListValue.value(Plist(map.properties().asSequence().mapNotNull { (key, value) ->
      readValue(value)?.let { key to it }
    }.toMap()), PlistValueType.DICT)
  }

  private fun readValue(value: JsonNode): PListValue? {
    return when {
      value.isObject -> {
        readDict(value as ObjectNode)
      }
      value.isArray -> {
        readArray(value)
      }
      else -> {
        readBasicValue(value)
      }
    }
  }

  private fun readArray(list: Iterable<JsonNode>): PListValue {
    return PListValue.value(list.mapNotNull { readValue(it) }, PlistValueType.ARRAY)
  }

  private fun readBasicValue(value: JsonNode): PListValue? {
    return when {
      value.isTextual -> PListValue.value(value.textValue(), PlistValueType.STRING)
      value.isBoolean -> PListValue.value(value.booleanValue(), PlistValueType.BOOLEAN)
      value.isIntegralNumber && value.canConvertToInt() -> PListValue.value(value.intValue(), PlistValueType.INTEGER)
      value.isNumber -> PListValue.value(value.doubleValue(), PlistValueType.REAL)
      else -> null
    }
  }
}
