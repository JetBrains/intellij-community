package org.jetbrains.plugins.textmate.plist

import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import java.io.IOException

class XmlPlistReaderForTests : PlistReaderCore {
  override fun read(bytes: ByteArray): Plist {
    return internalRead(readXmlAsModel(bytes))
  }

  companion object {
    private fun internalRead(root: XmlElement): Plist {
      if (root.children.isEmpty()) {
        return Plist.EMPTY_PLIST
      }

      if ("plist" != root.name) {
        throw IOException("Unknown xml format. Root element is '" + root.name + "'")
      }

      val dictElement = root.getChild("dict")
      return if (dictElement != null) readDict(dictElement).plist else Plist.EMPTY_PLIST
    }

    private fun readDict(dictElement: XmlElement): PListValue {
      val children = dictElement.children
      val map = buildMap {
        var i = 0
        while (i < children.size) {
          val keyElement = children[i]
          if ("key" == keyElement.name) {
            val attributeKey = keyElement.content
            i++
            if (attributeKey != null) {
              readValue(attributeKey, children[i])?.let { value ->
                put(attributeKey, value)
              }
            }
          }
          i++
        }
      }

      return PListValue.value(Plist(map), PlistValueType.DICT)
    }

    private fun readValue(key: String, valueElement: XmlElement): PListValue? {
      return when (val type = valueElement.name) {
        "dict" -> {
          readDict(valueElement)
        }
        "array" -> {
          readArray(key, valueElement)
        }
        else -> {
          readBasicValue(type, valueElement)
        }
      }
    }

    private fun readArray(key: String, element: XmlElement): PListValue {
      return PListValue.value(element.children.mapNotNull { readValue(key, it) }, PlistValueType.ARRAY)
    }

    private fun readBasicValue(type: String, valueElement: XmlElement): PListValue? {
      val content = valueElement.content

      return when (type) {
        "string" -> {
          PListValue.value(content.orEmpty(), PlistValueType.STRING)
        }
        "true" -> {
          PListValue.value(true, PlistValueType.BOOLEAN)
        }
        "false" -> {
          PListValue.value(false, PlistValueType.BOOLEAN)
        }
        "integer" -> {
          PListValue.value(content?.toIntOrNull() ?: 0, PlistValueType.INTEGER)
        }
        "real" -> {
          PListValue.value(content?.toDoubleOrNull() ?: 0.0, PlistValueType.REAL)
        }
        else -> null
      }
    }
  }
}