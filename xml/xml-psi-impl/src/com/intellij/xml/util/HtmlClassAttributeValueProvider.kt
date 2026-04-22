package com.intellij.xml.util

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.util.SmartList

fun getCustomHtmlClassAttributeValue(tag: XmlTag, getCustomClasses: (XmlAttribute) -> String?): String? {
  val result = SmartList<String>()
  var nativeClass: String? = null
  for (attribute in tag.attributes) {
    if (HtmlUtil.CLASS_ATTRIBUTE_NAME.equals(attribute.name, ignoreCase = true)) {
      getCustomClasses(attribute)?.let(result::add) ?: run {
        nativeClass = attribute.value
      }
    }
    else {
      getCustomClasses(attribute)?.let(result::add)
    }
  }
  if (result.isEmpty()) return null
  nativeClass?.let(result::add)
  return StringUtil.join(result, " ")
}
