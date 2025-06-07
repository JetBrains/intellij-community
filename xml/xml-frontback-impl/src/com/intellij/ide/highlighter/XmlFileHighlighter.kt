// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter

import com.intellij.lexer.DtdLexer
import com.intellij.lexer.Lexer
import com.intellij.lexer.XHtmlLexer
import com.intellij.lexer.XmlHighlightingLexer
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.XmlHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.progress.Cancellation
import com.intellij.psi.tree.IElementType
import com.intellij.psi.xml.XmlTokenType.TAG_WHITE_SPACE
import com.intellij.psi.xml.XmlTokenType.XML_ATTLIST_DECL_START
import com.intellij.psi.xml.XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER
import com.intellij.psi.xml.XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER
import com.intellij.psi.xml.XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN
import com.intellij.psi.xml.XmlTokenType.XML_BAD_CHARACTER
import com.intellij.psi.xml.XmlTokenType.XML_CHAR_ENTITY_REF
import com.intellij.psi.xml.XmlTokenType.XML_COMMENT_CHARACTERS
import com.intellij.psi.xml.XmlTokenType.XML_COMMENT_END
import com.intellij.psi.xml.XmlTokenType.XML_COMMENT_START
import com.intellij.psi.xml.XmlTokenType.XML_CONDITIONAL_COMMENT_END
import com.intellij.psi.xml.XmlTokenType.XML_CONDITIONAL_COMMENT_END_START
import com.intellij.psi.xml.XmlTokenType.XML_CONDITIONAL_COMMENT_START
import com.intellij.psi.xml.XmlTokenType.XML_CONDITIONAL_COMMENT_START_END
import com.intellij.psi.xml.XmlTokenType.XML_CONDITIONAL_IGNORE
import com.intellij.psi.xml.XmlTokenType.XML_CONDITIONAL_INCLUDE
import com.intellij.psi.xml.XmlTokenType.XML_CONDITIONAL_SECTION_END
import com.intellij.psi.xml.XmlTokenType.XML_CONDITIONAL_SECTION_START
import com.intellij.psi.xml.XmlTokenType.XML_DATA_CHARACTERS
import com.intellij.psi.xml.XmlTokenType.XML_DECL_END
import com.intellij.psi.xml.XmlTokenType.XML_DECL_START
import com.intellij.psi.xml.XmlTokenType.XML_DOCTYPE_END
import com.intellij.psi.xml.XmlTokenType.XML_DOCTYPE_PUBLIC
import com.intellij.psi.xml.XmlTokenType.XML_DOCTYPE_START
import com.intellij.psi.xml.XmlTokenType.XML_DOCTYPE_SYSTEM
import com.intellij.psi.xml.XmlTokenType.XML_ELEMENT_DECL_START
import com.intellij.psi.xml.XmlTokenType.XML_EMPTY_ELEMENT_END
import com.intellij.psi.xml.XmlTokenType.XML_END_TAG_START
import com.intellij.psi.xml.XmlTokenType.XML_ENTITY_DECL_START
import com.intellij.psi.xml.XmlTokenType.XML_ENTITY_REF_TOKEN
import com.intellij.psi.xml.XmlTokenType.XML_EQ
import com.intellij.psi.xml.XmlTokenType.XML_NAME
import com.intellij.psi.xml.XmlTokenType.XML_PI_END
import com.intellij.psi.xml.XmlTokenType.XML_PI_START
import com.intellij.psi.xml.XmlTokenType.XML_START_TAG_START
import com.intellij.psi.xml.XmlTokenType.XML_TAG_CHARACTERS
import com.intellij.psi.xml.XmlTokenType.XML_TAG_END
import com.intellij.psi.xml.XmlTokenType.XML_TAG_NAME
import com.intellij.util.containers.MultiMap

open class XmlFileHighlighter
@JvmOverloads
constructor(
  private val isDtd: Boolean = false,
  private val isXHtml: Boolean = false,
) : SyntaxHighlighterBase() {
  private object Holder {
    val ourMap: MultiMap<IElementType, TextAttributesKey> = MultiMap.create()

    init {
      ourMap.putValue(XML_DATA_CHARACTERS, XmlHighlighterColors.XML_TAG_DATA)

      for (type in sequenceOf(XML_COMMENT_START, XML_COMMENT_END, XML_COMMENT_CHARACTERS,
                              XML_CONDITIONAL_COMMENT_END, XML_CONDITIONAL_COMMENT_END_START,
                              XML_CONDITIONAL_COMMENT_START, XML_CONDITIONAL_COMMENT_START_END)) {
        ourMap.putValue(type, XmlHighlighterColors.XML_COMMENT)
      }

      for (type in sequenceOf(XML_START_TAG_START, XML_END_TAG_START, XML_TAG_END, XML_EMPTY_ELEMENT_END, TAG_WHITE_SPACE)) {
        ourMap.putValue(type, XmlHighlighterColors.XML_TAG)
      }
      for (type in sequenceOf(XML_TAG_NAME, XML_CONDITIONAL_IGNORE, XML_CONDITIONAL_INCLUDE)) {
        ourMap.putValues(type, listOf(XmlHighlighterColors.XML_TAG, XmlHighlighterColors.XML_TAG_NAME))
      }
      ourMap.putValues(XML_NAME, listOf(XmlHighlighterColors.XML_TAG, XmlHighlighterColors.XML_ATTRIBUTE_NAME))
      for (type in sequenceOf(XML_EQ, XML_TAG_CHARACTERS,
                              XML_ATTRIBUTE_VALUE_TOKEN, XML_ATTRIBUTE_VALUE_START_DELIMITER,
                              XML_ATTRIBUTE_VALUE_END_DELIMITER)) {
        ourMap.putValues(type, listOf(XmlHighlighterColors.XML_TAG, XmlHighlighterColors.XML_ATTRIBUTE_VALUE))
      }

      for (type in sequenceOf(XML_DECL_START, XML_DOCTYPE_START, XML_DOCTYPE_SYSTEM, XML_DOCTYPE_PUBLIC,
                              XML_ATTLIST_DECL_START, XML_ELEMENT_DECL_START, XML_ENTITY_DECL_START)) {
        ourMap.putValues(type, listOf(XmlHighlighterColors.XML_TAG, XmlHighlighterColors.XML_TAG_NAME))
      }

      for (type in sequenceOf(XML_CONDITIONAL_SECTION_START, XML_CONDITIONAL_SECTION_END, XML_DECL_END, XML_DOCTYPE_END)) {
        ourMap.putValues(type, listOf(XmlHighlighterColors.XML_PROLOGUE, XmlHighlighterColors.XML_TAG_NAME))
      }

      ourMap.putValue(XML_PI_START, XmlHighlighterColors.XML_PROLOGUE)
      ourMap.putValue(XML_PI_END, XmlHighlighterColors.XML_PROLOGUE)

      ourMap.putValue(XML_CHAR_ENTITY_REF, XmlHighlighterColors.XML_ENTITY_REFERENCE)
      ourMap.putValue(XML_ENTITY_REF_TOKEN, XmlHighlighterColors.XML_ENTITY_REFERENCE)

      ourMap.putValue(XML_BAD_CHARACTER, HighlighterColors.BAD_CHARACTER)

      Cancellation.computeInNonCancelableSection<Unit, Exception> {
        // PCE in static initializer breaks class initialization
        registerAdditionalHighlighters(ourMap)
        EMBEDDED_HIGHLIGHTERS.addExtensionPointListener(EmbeddedTokenHighlighterExtensionPointListener(ourMap), null)
      }
    }
  }

  internal class EmbeddedTokenHighlighterExtensionPointListener(
    private val myMap: MultiMap<IElementType, TextAttributesKey>,
  ) : ExtensionPointListener<EmbeddedTokenHighlighter> {
    override fun extensionAdded(
      extension: EmbeddedTokenHighlighter,
      pluginDescriptor: PluginDescriptor,
    ) {
      registerAdditionalHighlighters(myMap, extension)
    }

    override fun extensionRemoved(
      extension: EmbeddedTokenHighlighter,
      pluginDescriptor: PluginDescriptor,
    ) {
      val attributes = extension.embeddedTokenAttributes
      for (key in attributes.keySet()) {
        myMap.remove(key)
      }
      registerAdditionalHighlighters(myMap)
    }
  }

  override fun getHighlightingLexer(): Lexer {
    return when {
      isDtd -> DtdLexer(true)
      isXHtml -> XHtmlLexer(true)
      else -> XmlHighlightingLexer()
    }
  }

  override fun getTokenHighlights(tokenType: IElementType): Array<out TextAttributesKey> {
    synchronized(javaClass) {
      return Holder.ourMap[tokenType].toTypedArray()
    }
  }

  companion object {
    @JvmField
    val EMBEDDED_HIGHLIGHTERS: ExtensionPointName<EmbeddedTokenHighlighter> = create("com.intellij.embeddedTokenHighlighter")

    @JvmStatic
    fun registerAdditionalHighlighters(
      map: MultiMap<IElementType, TextAttributesKey>,
    ) {
      for (highlighter in EMBEDDED_HIGHLIGHTERS.extensionList) {
        registerAdditionalHighlighters(map, highlighter)
      }
    }

    private fun registerAdditionalHighlighters(
      map: MultiMap<IElementType, TextAttributesKey>,
      highlighter: EmbeddedTokenHighlighter,
    ) {
      val attributes = highlighter.embeddedTokenAttributes
      for ((key, value) in attributes.entrySet()) {
        if (!map.containsKey(key)) {
          map.putValues(key, value)
        }
      }
    }
  }
}
