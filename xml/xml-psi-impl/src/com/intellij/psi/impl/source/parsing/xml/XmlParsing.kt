// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.parsing.xml

import com.intellij.lang.PsiBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.TokenType
import com.intellij.psi.tree.ICustomParsingType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.ILazyParseableElementType
import com.intellij.psi.xml.XmlElementType.XML_ATTRIBUTE
import com.intellij.psi.xml.XmlElementType.XML_ATTRIBUTE_VALUE
import com.intellij.psi.xml.XmlElementType.XML_CDATA
import com.intellij.psi.xml.XmlElementType.XML_COMMENT
import com.intellij.psi.xml.XmlElementType.XML_DOCTYPE
import com.intellij.psi.xml.XmlElementType.XML_DOCUMENT
import com.intellij.psi.xml.XmlElementType.XML_ENTITY_REF
import com.intellij.psi.xml.XmlElementType.XML_PROCESSING_INSTRUCTION
import com.intellij.psi.xml.XmlElementType.XML_PROLOG
import com.intellij.psi.xml.XmlElementType.XML_TAG
import com.intellij.psi.xml.XmlElementType.XML_TEXT
import com.intellij.psi.xml.XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER
import com.intellij.psi.xml.XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER
import com.intellij.psi.xml.XmlTokenType.XML_BAD_CHARACTER
import com.intellij.psi.xml.XmlTokenType.XML_CDATA_END
import com.intellij.psi.xml.XmlTokenType.XML_CDATA_START
import com.intellij.psi.xml.XmlTokenType.XML_CHAR_ENTITY_REF
import com.intellij.psi.xml.XmlTokenType.XML_COMMENT_CHARACTERS
import com.intellij.psi.xml.XmlTokenType.XML_COMMENT_END
import com.intellij.psi.xml.XmlTokenType.XML_COMMENT_START
import com.intellij.psi.xml.XmlTokenType.XML_CONDITIONAL_COMMENT_END
import com.intellij.psi.xml.XmlTokenType.XML_CONDITIONAL_COMMENT_END_START
import com.intellij.psi.xml.XmlTokenType.XML_CONDITIONAL_COMMENT_START
import com.intellij.psi.xml.XmlTokenType.XML_CONDITIONAL_COMMENT_START_END
import com.intellij.psi.xml.XmlTokenType.XML_DOCTYPE_END
import com.intellij.psi.xml.XmlTokenType.XML_DOCTYPE_START
import com.intellij.psi.xml.XmlTokenType.XML_EMPTY_ELEMENT_END
import com.intellij.psi.xml.XmlTokenType.XML_END_TAG_START
import com.intellij.psi.xml.XmlTokenType.XML_ENTITY_REF_TOKEN
import com.intellij.psi.xml.XmlTokenType.XML_EQ
import com.intellij.psi.xml.XmlTokenType.XML_NAME
import com.intellij.psi.xml.XmlTokenType.XML_PI_END
import com.intellij.psi.xml.XmlTokenType.XML_PI_START
import com.intellij.psi.xml.XmlTokenType.XML_REAL_WHITE_SPACE
import com.intellij.psi.xml.XmlTokenType.XML_START_TAG_START
import com.intellij.psi.xml.XmlTokenType.XML_TAG_CHARACTERS
import com.intellij.psi.xml.XmlTokenType.XML_TAG_END
import com.intellij.util.containers.Stack
import com.intellij.xml.parsing.XmlParserBundle.message

/*
 * @author max
 */
open class XmlParsing(
  @JvmField
  protected val myBuilder: PsiBuilder,
) {
  private val tagNamesStack = Stack<String>()

  fun parseDocument() {
    val document = mark()

    while (isCommentToken(token())) {
      parseComment()
    }

    parseProlog()

    var rootTagCount = 0
    var error: PsiBuilder.Marker? = null
    while (!eof()) {
      val tt = token()
      if (tt === XML_START_TAG_START) {
        error = flushError(error)
        rootTagCount++
        parseTag(rootTagCount > 1)
      }
      else if (isCommentToken(tt)) {
        error = flushError(error)
        parseComment()
      }
      else if (tt === XML_PI_START) {
        error = flushError(error)
        parseProcessingInstruction()
      }
      else if (tt === XML_REAL_WHITE_SPACE) {
        error = flushError(error)
        advance()
      }
      else {
        if (error == null) error = mark()
        advance()
      }
    }

    error?.error(message("xml.parsing.top.level.element.is.not.completed"))

    if (rootTagCount == 0) {
      val rootTag = mark()
      error = mark()
      error.error(message("xml.parsing.absent.root.tag"))
      rootTag.done(XML_TAG)
    }

    document.done(XML_DOCUMENT)
  }

  private fun parseDoctype() {
    assert(token() === XML_DOCTYPE_START) { "Doctype start expected" }
    val doctype = mark()
    advance()

    while (token() !== XML_DOCTYPE_END && !eof()) advance()
    if (eof()) {
      error(message("xml.parsing.unexpected.end.of.file"))
    }
    else {
      advance()
    }

    doctype.done(XML_DOCTYPE)
  }

  protected fun parseTag(multipleRootTagError: Boolean) {
    assert(token() === XML_START_TAG_START) { "Tag start expected" }
    val tag = mark()

    val tagName = parseTagHeader(multipleRootTagError, tag)
    if (tagName == null) return

    val content = mark()
    parseTagContent()

    if (token() === XML_END_TAG_START) {
      val footer = mark()
      advance()

      if (token() === XML_NAME) {
        val endName = myBuilder.tokenText
        if (tagName != endName && tagNamesStack.contains(endName)) {
          footer.rollbackTo()
          tagNamesStack.pop()
          tag.doneBefore(XML_TAG, content, message("xml.parsing.named.element.is.not.closed", tagName))
          content.drop()
          return
        }

        advance()
      }
      else {
        error(message("xml.parsing.closing.tag.name.missing"))
      }
      footer.drop()

      while (token() !== XML_TAG_END
             && token() !== XML_START_TAG_START
             && token() !== XML_END_TAG_START
             && !eof()
      ) {
        error(message("xml.parsing.unexpected.token"))
        advance()
      }

      if (token() === XML_TAG_END) {
        advance()
      }
      else {
        error(message("xml.parsing.closing.tag.is.not.done"))
      }
    }
    else {
      error(message("xml.parsing.unexpected.end.of.file"))
    }

    content.drop()
    tagNamesStack.pop()
    tag.done(XML_TAG)
  }

  private fun parseTagHeader(multipleRootTagError: Boolean, tag: PsiBuilder.Marker): String? {
    if (multipleRootTagError) {
      val error = mark()
      advance()
      error.error(message("xml.parsing.multiple.root.tags"))
    }
    else {
      advance()
    }

    val tagName: String?
    if (token() !== XML_NAME || myBuilder.rawLookup(-1) === TokenType.WHITE_SPACE) {
      error(message("xml.parsing.tag.name.expected"))
      tagName = ""
    }
    else {
      tagName = myBuilder.tokenText
      checkNotNull(tagName)
      advance()
    }
    tagNamesStack.push(tagName)

    do {
      val tt = token()
      if (tt === XML_NAME) {
        parseAttribute()
      }
      else if (tt === XML_CHAR_ENTITY_REF || tt === XML_ENTITY_REF_TOKEN) {
        parseReference()
      }
      else {
        break
      }
    }
    while (true)

    if (token() === XML_EMPTY_ELEMENT_END) {
      advance()
      tagNamesStack.pop()
      tag.done(XML_TAG)
      return null
    }

    if (token() === XML_TAG_END) {
      advance()
    }
    else {
      error(message("xml.parsing.tag.start.is.not.closed"))
      tagNamesStack.pop()
      tag.done(XML_TAG)
      return null
    }

    if (tagNamesStack.size > BALANCING_DEPTH_THRESHOLD) {
      error(message("xml.parsing.way.too.unbalanced"))
      tag.done(XML_TAG)
      return null
    }

    return tagName
  }

  fun parseTagContent() {
    var xmlText: PsiBuilder.Marker? = null
    while (true) {
      val tt = token()
      if (tt == null || tt === XML_END_TAG_START) {
        break
      }

      if (tt === XML_START_TAG_START) {
        xmlText = terminateText(xmlText)
        parseTag(false)
      }
      else if (tt === XML_PI_START) {
        xmlText = terminateText(xmlText)
        parseProcessingInstruction()
      }
      else if (tt === XML_ENTITY_REF_TOKEN) {
        xmlText = terminateText(xmlText)
        parseReference()
      }
      else if (tt === XML_CHAR_ENTITY_REF) {
        xmlText = startText(xmlText)
        parseReference()
      }
      else if (tt === XML_CDATA_START) {
        xmlText = startText(xmlText)
        parseCData()
      }
      else if (isCommentToken(tt)) {
        xmlText = terminateText(xmlText)
        parseComment()
      }
      else if (tt === XML_BAD_CHARACTER) {
        xmlText = startText(xmlText)
        val error = mark()
        advance()
        error.error(message("xml.parsing.unescaped.ampersand.or.nonterminated.character.entity.reference"))
      }
      else if (tt is ICustomParsingType || tt is ILazyParseableElementType) {
        xmlText = terminateText(xmlText)
        advance()
      }
      else {
        xmlText = startText(xmlText)
        advance()
      }
    }

    terminateText(xmlText)
  }

  protected open fun isCommentToken(tt: IElementType?): Boolean {
    return tt === XML_COMMENT_START
  }

  private fun startText(xmlText: PsiBuilder.Marker?): PsiBuilder.Marker {
    return xmlText ?: mark()
  }

  protected fun mark(): PsiBuilder.Marker {
    return myBuilder.mark()
  }

  private fun parseCData() {
    assert(token() === XML_CDATA_START)
    val cdata = mark()
    while (token() !== XML_CDATA_END && !eof()) {
      advance()
    }

    if (!eof()) {
      advance()
    }

    cdata.done(XML_CDATA)
  }

  protected open fun parseComment() {
    val comment = mark()
    advance()
    while (true) {
      val tt = token()
      if (tt === XML_COMMENT_CHARACTERS
          || tt === XML_CONDITIONAL_COMMENT_START
          || tt === XML_CONDITIONAL_COMMENT_START_END
          || tt === XML_CONDITIONAL_COMMENT_END_START
          || tt === XML_CONDITIONAL_COMMENT_END
      ) {
        advance()
        continue
      }
      else if (tt === XML_BAD_CHARACTER) {
        val error = mark()
        advance()
        error.error(message("xml.parsing.bad.character"))
        continue
      }
      if (tt === XML_COMMENT_END) {
        advance()
      }
      break
    }
    comment.done(XML_COMMENT)
  }

  private fun parseReference() {
    if (token() === XML_CHAR_ENTITY_REF) {
      advance()
    }
    else if (token() === XML_ENTITY_REF_TOKEN) {
      val ref = mark()
      advance()
      ref.done(XML_ENTITY_REF)
    }
    else {
      assert(false) { "Unexpected token" }
    }
  }

  private fun parseAttribute() {
    assert(token() === XML_NAME)
    val att = mark()
    advance()
    if (token() === XML_EQ) {
      advance()
      parseAttributeValue()
    }
    else {
      error(message("xml.parsing.expected.attribute.eq.sign"))
    }
    att.done(XML_ATTRIBUTE)
  }

  private fun parseAttributeValue() {
    val attValue = mark()
    if (token() === XML_ATTRIBUTE_VALUE_START_DELIMITER) {
      while (true) {
        val tt = token()
        if (tt == null
            || tt === XML_ATTRIBUTE_VALUE_END_DELIMITER
            || tt === XML_END_TAG_START
            || tt === XML_EMPTY_ELEMENT_END
            || tt === XML_START_TAG_START
        ) {
          break
        }

        if (tt === XML_BAD_CHARACTER) {
          val error = mark()
          advance()
          error.error(message("xml.parsing.unescaped.ampersand.or.nonterminated.character.entity.reference"))
        }
        else if (tt === XML_ENTITY_REF_TOKEN) {
          parseReference()
        }
        else {
          advance()
        }
      }

      if (token() === XML_ATTRIBUTE_VALUE_END_DELIMITER) {
        advance()
      }
      else {
        error(message("xml.parsing.unclosed.attribute.value"))
      }
    }
    else {
      error(message("xml.parsing.attribute.value.expected"))
    }

    attValue.done(XML_ATTRIBUTE_VALUE)
  }

  private fun parseProlog() {
    val prolog = mark()
    while (true) {
      val tt = token()
      if (tt === XML_PI_START) {
        parseProcessingInstruction()
      }
      else if (tt === XML_DOCTYPE_START) {
        parseDoctype()
      }
      else if (isCommentToken(tt)) {
        parseComment()
      }
      else if (tt === XML_REAL_WHITE_SPACE) {
        advance()
      }
      else {
        break
      }
    }
    prolog.done(XML_PROLOG)
  }

  private fun parseProcessingInstruction() {
    assert(token() === XML_PI_START)
    val pi = mark()
    advance()
    if (token() !== XML_NAME) {
      error(message("xml.parsing.processing.instruction.name.expected"))
    }
    else {
      advance()
    }

    val tokenType = token()
    if (tokenType === XML_TAG_CHARACTERS) {
      while (token() === XML_TAG_CHARACTERS) {
        advance()
      }
    }
    else {
      while (token() === XML_NAME) {
        advance()
        if (token() === XML_EQ) {
          advance()
        }
        else {
          error(message("xml.parsing.expected.attribute.eq.sign"))
        }
        parseAttributeValue()
      }
    }

    if (token() === XML_PI_END) {
      advance()
    }
    else {
      error(message("xml.parsing.unterminated.processing.instruction"))
    }

    pi.done(XML_PROCESSING_INSTRUCTION)
  }

  protected fun token(): IElementType? {
    return myBuilder.tokenType
  }

  protected fun eof(): Boolean {
    return myBuilder.eof()
  }

  protected fun advance() {
    myBuilder.advanceLexer()
  }

  private fun error(message: @NlsContexts.ParsingError String) {
    myBuilder.error(message)
  }

  private companion object {
    private const val BALANCING_DEPTH_THRESHOLD = 1000

    private fun flushError(error: PsiBuilder.Marker?): PsiBuilder.Marker? {
      error?.error(message("xml.parsing.unexpected.tokens"))
      return null
    }

    private fun terminateText(xmlText: PsiBuilder.Marker?): PsiBuilder.Marker? {
      xmlText?.done(XML_TEXT)
      return null
    }
  }
}
