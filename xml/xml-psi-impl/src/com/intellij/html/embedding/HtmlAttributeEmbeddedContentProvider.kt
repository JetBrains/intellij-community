// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.html.embedding

import com.intellij.lexer.BaseHtmlLexer
import com.intellij.lexer.isAttributeEmbedmentToken
import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.IElementType
import com.intellij.psi.xml.XmlTokenType

abstract class HtmlAttributeEmbeddedContentProvider(lexer: BaseHtmlLexer)
  : BaseHtmlEmbeddedContentProvider(lexer) {

  private var myTagNameRead = false
  private var myWithinTag = false
  private var myTagName: CharSequence? = null
  private var myAttributeName: CharSequence? = null

  protected val attributeName: CharSequence?
    get() = myAttributeName
  protected val tagName: CharSequence?
    get() = myTagName

  protected abstract fun isInterestedInTag(tagName: CharSequence): Boolean

  protected abstract fun isInterestedInAttribute(attributeName: CharSequence): Boolean

  override fun handleToken(tokenType: IElementType, range: TextRange) {
    when (tokenType) {
      XmlTokenType.XML_START_TAG_START -> {
        myTagNameRead = false
        embedment = false
        myWithinTag = false
        myTagName = null
        myAttributeName = null
      }
      XmlTokenType.XML_NAME -> {
        val baseLexer = lexer.delegate
        if (!myTagNameRead) {
          val tagName = range.subSequence(baseLexer.bufferSequence)
          myWithinTag = isInterestedInTag(tagName)
          if (myWithinTag) {
            this.myTagName = tagName
          }
          myTagNameRead = true
        }
        else if (myWithinTag) {
          val attributeName = range.subSequence(baseLexer.bufferSequence)
          embedment = isInterestedInAttribute(attributeName)
          if (embedment) {
            this.myAttributeName = attributeName
          }
          else {
            this.myAttributeName = null
          }
        }
      }
      XmlTokenType.XML_TAG_END, XmlTokenType.XML_END_TAG_START, XmlTokenType.XML_EMPTY_ELEMENT_END -> {
        myTagNameRead = tokenType === XmlTokenType.XML_END_TAG_START
        myWithinTag = false
        myTagName = null
        myAttributeName = null
        embedment = false
      }
    }
  }

  override fun isStartOfEmbedment(tokenType: IElementType): Boolean =
    myAttributeName != null && isAttributeEmbedmentToken(tokenType)

  protected open fun isAttributeEmbedmentToken(tokenType: IElementType): Boolean =
    lexer.isAttributeEmbedmentToken(tokenType, myAttributeName!!)

  override fun findTheEndOfEmbedment(): Pair<Int, Int> {
    val baseLexer = lexer.delegate
    val position = baseLexer.currentPosition
    while (true) {
      if (!isAttributeEmbedmentToken(baseLexer.tokenType ?: break)) break
      baseLexer.advance()
    }
    val result = Pair(baseLexer.tokenStart, baseLexer.state)
    baseLexer.restore(position)
    return result
  }

  override fun restoreState(state: Any?) {
    if (state == null) {
      myTagNameRead = false
      myWithinTag = false
      myTagName = null
      myAttributeName = null
      embedment = false
      return
    }
    if (state !is AttributeState) return
    myTagNameRead = state.tagNameRead
    myWithinTag = state.withinTag
    myTagName = state.tagName
    myAttributeName = state.attributeName
    embedment = state.embedment
  }

  override fun hasState(): Boolean = myTagNameRead || myWithinTag || myTagName != null || myAttributeName != null || embedment

  override fun getState(): Any? =
    if (hasState())
      AttributeState(myTagNameRead, myWithinTag, myTagName, myAttributeName, embedment)
    else
      null

  open class AttributeState(val tagNameRead: Boolean, val withinTag: Boolean, val tagName: CharSequence?,
                            val attributeName: CharSequence?, embedment: Boolean) : BaseState(embedment)

}