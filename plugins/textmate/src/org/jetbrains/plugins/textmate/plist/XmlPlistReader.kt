package org.jetbrains.plugins.textmate.plist

import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import java.io.IOException
import java.io.InputStream
import java.lang.Boolean
import kotlin.text.toDouble
import kotlin.text.toInt

class XmlPlistReader : PlistReader {
  override fun read(inputStream: InputStream): Plist {
    return internalRead(readXmlAsModel(inputStream))
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

      return when {
        "string" == type && content != null -> {
          PListValue.value(content, PlistValueType.STRING)
        }
        "true" == type -> {
          PListValue.value(Boolean.TRUE, PlistValueType.BOOLEAN)
        }
        "false" == type -> {
          PListValue.value(Boolean.FALSE, PlistValueType.BOOLEAN)
        }
        "integer" == type && content != null -> {
          PListValue.value(content.toInt(), PlistValueType.INTEGER)
        }
        "real" == type && content != null -> {
          PListValue.value(content.toDouble(), PlistValueType.REAL)
        }
        else -> null
      }
    }
  }
}