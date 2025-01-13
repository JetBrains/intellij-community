package org.jetbrains.plugins.textmate.plist

import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.plugins.textmate.plist.PListValue.Companion.value
import java.io.IOException
import java.io.InputStream
import java.lang.Boolean
import java.text.ParseException
import kotlin.String
import kotlin.let

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

      return value(Plist(map), PlistValueType.DICT)
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
      return value(element.children.mapNotNull { readValue(key, it) }, PlistValueType.ARRAY)
    }

    private fun readBasicValue(type: String, valueElement: XmlElement): PListValue? {
      val content = valueElement.content

      return when {
        "string" == type && content != null -> {
          value(content, PlistValueType.STRING)
        }
        "true" == type -> {
          value(Boolean.TRUE, PlistValueType.BOOLEAN)
        }
        "false" == type -> {
          value(Boolean.FALSE, PlistValueType.BOOLEAN)
        }
        "integer" == type && content != null -> {
          value(content.toInt(), PlistValueType.INTEGER)
        }
        "real" == type && content != null -> {
          value(content.toDouble(), PlistValueType.REAL)
        }
        "date" == type && content != null -> {
          try {
            value(Plist.dateFormatter().parse(content), PlistValueType.DATE)
          }
          catch (e: ParseException) {
            throw IOException(e)
          }
        }
        else -> null
      }
    }
  }
}
