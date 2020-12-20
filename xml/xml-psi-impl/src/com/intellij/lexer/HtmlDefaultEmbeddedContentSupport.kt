// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("DEPRECATION")

package com.intellij.lexer

import com.intellij.html.embedding.HtmlEmbeddedContentProvider
import com.intellij.html.embedding.HtmlEmbeddedContentSupport
import com.intellij.html.embedding.HtmlEmbedmentInfo
import com.intellij.html.embedding.HtmlTagEmbeddedContentProvider
import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.tree.IElementType
import com.intellij.psi.xml.XmlTokenType.XML_DATA_CHARACTERS
import com.intellij.xml.util.HtmlUtil
import java.util.*

class HtmlDefaultEmbeddedContentSupport : HtmlEmbeddedContentSupport {

  override fun createEmbeddedContentProviders(lexer: BaseHtmlLexer): List<HtmlEmbeddedContentProvider> =
    listOf(HtmlDefaultTagEmbeddedContentProvider(lexer))

}

private class HtmlDefaultTagEmbeddedContentProvider(lexer: BaseHtmlLexer) : HtmlTagEmbeddedContentProvider(lexer) {

  private val infoCache = HashMap<Pair<String, String?>, HtmlEmbedmentInfo?>()

  override fun isInterestedInTag(tagName: CharSequence): Boolean =
    namesEqual(tagName, HtmlUtil.SCRIPT_TAG_NAME) || namesEqual(tagName, HtmlUtil.STYLE_TAG_NAME)

  override fun isInterestedInAttribute(attributeName: CharSequence): Boolean =
    namesEqual(attributeName, HtmlUtil.TYPE_ATTRIBUTE_NAME)
    || (namesEqual(attributeName, HtmlUtil.LANGUAGE_ATTRIBUTE_NAME) && namesEqual(tagName, HtmlUtil.SCRIPT_TAG_NAME))

  override fun createEmbedmentInfo(): HtmlEmbedmentInfo? {
    val attributeValue = attributeValue?.trim()?.toString()
    return infoCache.getOrPut(Pair(tagName!!.toString(), attributeValue)) {
      if (namesEqual(tagName, HtmlUtil.STYLE_TAG_NAME)) {
        styleLanguage(attributeValue)?.let {
          HtmlEmbeddedContentSupport.getStyleTagEmbedmentInfo(it)
        }
      }
      else {
        scriptEmbedmentInfo(attributeValue)
        ?: object : HtmlEmbedmentInfo {
          override fun getElementType(): IElementType? = XML_DATA_CHARACTERS
          override fun createHighlightingLexer(): Lexer? = null
        }
      }
    }
  }

  private fun styleLanguage(styleLang: String?): Language? {
    val cssLanguage = Language.findLanguageByID("CSS")
    if (styleLang != null && !styleLang.equals("text/css", ignoreCase = true)) {
      cssLanguage
        ?.dialects
        ?.firstOrNull { dialect ->
          dialect.mimeTypes.any { it.equals(styleLang, ignoreCase = true) }
        }
        ?.let { return it }
    }
    return cssLanguage
  }

  private fun scriptEmbedmentInfo(mimeType: String?): HtmlEmbedmentInfo? =
    if (mimeType != null)
      Language.findInstancesByMimeType(if (lexer.isCaseInsensitive) StringUtil.toLowerCase(mimeType) else mimeType)
        .asSequence()
        .plus(if (StringUtil.containsIgnoreCase(mimeType, "template")) listOf(HTMLLanguage.INSTANCE) else emptyList())
        .map { HtmlEmbeddedContentSupport.getScriptTagEmbedmentInfo(it) }
        .firstOrNull { it != null }
    else
      Language.findLanguageByID("JavaScript")?.let {
        HtmlEmbeddedContentSupport.getScriptTagEmbedmentInfo(it)
      }

}