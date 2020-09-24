// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.html.embedding

import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.lexer.BaseHtmlLexer
import com.intellij.lexer.isTagEmbedmentStartToken
import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.IElementType
import com.intellij.psi.xml.XmlTokenType
import com.intellij.util.text.CharArrayUtil

abstract class HtmlTagEmbeddedContentProvider(lexer: BaseHtmlLexer) : BaseHtmlEmbeddedContentProvider(lexer) {

  private var myAttributeValue: CharSequence? = null
  private var myAttributeName: CharSequence? = null
  private var myTagName: CharSequence? = null
  private var myTagNameRead: Boolean = false
  private var myWithinTag: Boolean = false
  private var myReadAttributeValue: Boolean = false

  protected val attributeValue: CharSequence?
    get() = myAttributeValue
  protected val attributeName: CharSequence?
    get() = myAttributeName
  protected val tagName: CharSequence?
    get() = myTagName
  protected val withinTag: Boolean
    get() = myWithinTag

  protected abstract fun isInterestedInTag(tagName: CharSequence): Boolean

  protected abstract fun isInterestedInAttribute(attributeName: CharSequence): Boolean

  override fun handleToken(tokenType: IElementType, range: TextRange) {
    when (tokenType) {
      XmlTokenType.XML_START_TAG_START -> {
        myTagNameRead = false
        myReadAttributeValue = false
        myWithinTag = false
      }
      XmlTokenType.XML_NAME -> {
        val baseLexer = lexer.delegate
        if (!myTagNameRead) {
          val tagName = range.subSequence(baseLexer.bufferSequence)
          myWithinTag = isInterestedInTag(tagName)
          if (myWithinTag) {
            myTagName = tagName
          }
          else {
            myTagName = null
          }
          myTagNameRead = true
          myAttributeName = null
          myAttributeValue = null
        }
        else if (myWithinTag) {
          val attributeName = range.subSequence(baseLexer.bufferSequence)
          myReadAttributeValue = isInterestedInAttribute(attributeName)
          if (myReadAttributeValue) {
            this.myAttributeName = attributeName
            this.myAttributeValue = attributeName
          }
        }
        embedment = false
      }
      XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN -> {
        if (myReadAttributeValue) {
          myAttributeValue = range.subSequence(lexer.delegate.bufferSequence)
        }
      }
      XmlTokenType.XML_TAG_END, XmlTokenType.XML_EMPTY_ELEMENT_END -> {
        embedment = tokenType == XmlTokenType.XML_TAG_END && myWithinTag
        myWithinTag = false
        myTagNameRead = false
        myReadAttributeValue = false
        if (!embedment) {
          myTagName = null
          myAttributeName = null
          myAttributeValue = null
        }
      }
      XmlTokenType.XML_END_TAG_START -> {
        myTagNameRead = true
        myReadAttributeValue = false
        myWithinTag = false
      }
    }
  }

  override fun createEmbedment(tokenType: IElementType): HtmlEmbedment? =
    super.createEmbedment(tokenType)?.takeIf { !it.range.isEmpty }

  override fun isStartOfEmbedment(tokenType: IElementType): Boolean =
    myTagName != null && isTagEmbedmentStartToken(tokenType)

  protected open fun isTagEmbedmentStartToken(tokenType: IElementType): Boolean =
    lexer.isTagEmbedmentStartToken(tokenType, myTagName!!)

  override fun findTheEndOfEmbedment(): Pair<Int, Int> {
    tagName!!
    val baseLexer = lexer.delegate
    val position = baseLexer.currentPosition
    val bufferEnd = baseLexer.bufferEnd
    var lastState: Int
    var lastStart: Int

    val buf = baseLexer.bufferSequence
    val bufArray = CharArrayUtil.fromSequenceWithoutCopying(buf)

    while (true) {
      FindTagEnd@ while (baseLexer.tokenType.let { it != null && it !== XmlTokenType.XML_END_TAG_START }) {
        if (baseLexer.tokenType === XmlTokenType.XML_COMMENT_CHARACTERS) {
          // we should terminate on first occurence of </
          val end = baseLexer.tokenEnd

          for (i in baseLexer.tokenStart until end) {
            if (i + 1 < end
                && (if (bufArray != null) bufArray[i] else buf[i]) == '<'
                && (if (bufArray != null) bufArray[i + 1] else buf[i + 1]) == '/') {
              baseLexer.start(buf, i, bufferEnd, 0)
              baseLexer.tokenType
              break@FindTagEnd
            }
          }
        }
        baseLexer.advance()
      }
      lastState = baseLexer.state
      lastStart = baseLexer.tokenStart
      if (baseLexer.tokenType == null) break
      baseLexer.advance()
      while (XmlTokenType.WHITESPACES.contains(baseLexer.tokenType)) {
        baseLexer.advance()
      }
      if (baseLexer.tokenType == null) break
      if (baseLexer.tokenType === XmlTokenType.XML_NAME) {
        val tokenText = buf.subSequence(baseLexer.tokenStart, baseLexer.tokenEnd)
        if (namesEqual(tagName, tokenText)
            || namesEqual(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, tokenText)) {
          break // really found end
        }
      }
    }
    baseLexer.restore(position)
    return Pair(lastStart, lastState)
  }

  override fun restoreState(state: Any?) {
    if (state == null) {
      myTagNameRead = false
      myWithinTag = false
      myTagName = null
      myAttributeName = null
      myReadAttributeValue = false
      myAttributeValue = null
      embedment = false
      return
    }
    if (state !is TagState) return
    myTagNameRead = state.tagNameRead
    myWithinTag = state.withinTag
    myTagName = state.tagName
    myAttributeName = state.attributeName
    myReadAttributeValue = state.readAttributeValue
    myAttributeValue = state.attributeValue
    embedment = state.embedment
  }

  override fun hasState(): Boolean = myTagNameRead || myWithinTag || myTagName != null || myAttributeName != null || embedment

  override fun getState(): Any? =
    if (hasState())
      TagState(myTagNameRead, myWithinTag, myTagName, myAttributeName, myReadAttributeValue, myAttributeValue, embedment)
    else
      null

  open class TagState(val tagNameRead: Boolean, val withinTag: Boolean, val tagName: CharSequence?,
                      val attributeName: CharSequence?, val readAttributeValue: Boolean,
                      val attributeValue: CharSequence?, embedment: Boolean) : BaseState(embedment)


}