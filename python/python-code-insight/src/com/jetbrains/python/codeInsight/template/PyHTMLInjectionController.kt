// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.template

import com.intellij.xml.util.HtmlUtil


private val OBVIOUS_HTML_TAGS = setOf("html", "div", "span", "body", "p", "h1", "h2", "h3", "img",
                                      "table", "ul", "ol", "li", "a", "img", "form", "script")

fun looksLikeHTML(content: String): Boolean {
  if (content.isEmpty()) return false

  val cleanContent = content.trim().replace(Regex("[\n\r]"), "")

  if (cleanContent.startsWith("<")) {

    val tagNamePattern = "<(\\w+)".toRegex()
    val tagNameMatch = tagNamePattern.find(cleanContent)
    if (tagNameMatch != null) {
      val tagName = tagNameMatch.groupValues[1]
      if (OBVIOUS_HTML_TAGS.contains(tagName) ||
          HtmlUtil.isHtmlBlockTag(tagName, false) ||
          HtmlUtil.isTagWithOptionalEnd(tagName, false)) {
        return true
      }
    }
  }
  return false
}
