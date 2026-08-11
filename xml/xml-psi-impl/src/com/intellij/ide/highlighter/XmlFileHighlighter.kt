// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.nullize
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle

open class XmlFileHighlighter
@JvmOverloads
constructor(val isDtd: Boolean = false, val isXHtml: Boolean = false) : SyntaxHighlighterBase() {
  private object Holder {
    @JvmStatic
    val ourMap: AttributeArrayMapStorage

    init {
      val ourMap: MultiMap<IElementType, TextAttributesKey> = MultiMap.create()
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
      this.ourMap = createMapAndListenForExtensionChanges(ourMap)
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
    return Holder.ourMap.getOrDefault(tokenType, TextAttributesKey.EMPTY_ARRAY)
  }

  companion object {
    private val EMBEDDED_HIGHLIGHTERS: ExtensionPointName<EmbeddedTokenHighlighter> = ExtensionPointName.create("com.intellij.embeddedTokenHighlighter")

    fun createMapAndListenForExtensionChanges(attributes: MultiMap<IElementType, TextAttributesKey>,
                                              fixer: (Array<TextAttributesKey>) -> Array<TextAttributesKey> = { it }
    ): AttributeArrayMapStorage {
      val map = java.util.Map.copyOf(attributes.entrySet().associate { e -> e.key to fixer(e.value.toTypedArray()) })
      val storage = AttributeArrayMapStorage(map, fixer)
      return storage
    }
  }

  class AttributeArrayMapStorage {
    @Volatile
    private var map:Map<IElementType, Array<TextAttributesKey>>
    /**
     * we need to update [map] atomically when the [EMBEDDED_HIGHLIGHTERS] extensions change
     */
    private val updater: VarHandle = MethodHandles.privateLookupIn(AttributeArrayMapStorage::class.java, MethodHandles.lookup()).findVarHandle(AttributeArrayMapStorage::class.java, "map", Map::class.java)

    constructor(map: Map<IElementType, Array<TextAttributesKey>>, fixer: (Array<TextAttributesKey>) -> Array<TextAttributesKey>) {
      this.map = map
      assert (updater.getVolatile(this) === map)
      Cancellation.computeInNonCancelableSection<Unit, Exception> {
        // PCE in static initializer breaks class initialization
        EMBEDDED_HIGHLIGHTERS.point.addExtensionPointListener(EmbeddedTokenHighlighterExtensionPointListener(fixer), true, null)
      }
    }

    fun getOrDefault(token: IElementType, defaultValue: Array<out TextAttributesKey>): Array<out TextAttributesKey> {
      return map.getOrDefault(token, defaultValue)
    }

    internal fun registerChangedHighlighters(
      fixer: (Array<TextAttributesKey>) -> Array<TextAttributesKey>,
      addedAttributes: MultiMap<IElementType, TextAttributesKey>,
      removedAttributes: MultiMap<IElementType, TextAttributesKey>
    ) {
      do {
        val oldMap = map
        val relevantOldMap = oldMap.toMutableMap()
        for ((removedToken, removedAttributes) in removedAttributes.entrySet()) {
          relevantOldMap.merge(removedToken, TextAttributesKey.EMPTY_ARRAY) { existingAttributes, _->
            val fixedRemoved = ContainerUtil.newHashSet(*fixer(removedAttributes.toTypedArray()))
            ContainerUtil.subtract(ContainerUtil.newHashSet(*existingAttributes), fixedRemoved).toTypedArray().nullize()
          }
        }
        val newMap = HashMap.newHashMap<IElementType, Array<TextAttributesKey>>(relevantOldMap.size + addedAttributes.size() )
        for ((key, value) in addedAttributes.entrySet()) {
          if (!relevantOldMap.containsKey(key)) {
            newMap.put(key, fixer(value.toTypedArray()))
          }
        }
        if (newMap.isEmpty() && removedAttributes.isEmpty) {
          break
        }
        newMap.putAll(relevantOldMap)
      } while(!updater.compareAndSet(this, oldMap, java.util.Map.copyOf(newMap)))
    }
    /**
     * listens for [XmlFileHighlighter.EMBEDDED_HIGHLIGHTERS] extensions and updates the [map] with attributes read from new extension set
     * calls [fixer] on this attributes list before storing it in the map.
     */
    private inner class EmbeddedTokenHighlighterExtensionPointListener(
      private val fixer: (Array<TextAttributesKey>) -> Array<TextAttributesKey>,
    ) : ExtensionPointListener<EmbeddedTokenHighlighter> {
      override fun extensionAdded(extension: EmbeddedTokenHighlighter, pluginDescriptor: PluginDescriptor) {
        registerChangedHighlighters(fixer,
                                    addedAttributes = extension.embeddedTokenAttributes,
                                    removedAttributes = MultiMap.empty())
      }

      override fun extensionRemoved(extension: EmbeddedTokenHighlighter, pluginDescriptor: PluginDescriptor) {
        registerChangedHighlighters(fixer,
                                    addedAttributes = MultiMap.empty(),
                                    removedAttributes = extension.embeddedTokenAttributes)
      }
    }
  }

}
