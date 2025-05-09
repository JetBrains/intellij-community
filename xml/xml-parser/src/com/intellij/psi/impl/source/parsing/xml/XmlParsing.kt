// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

    fun flushError() {
      error?.let {
        it.error(message("xml.parsing.unexpected.tokens"))
        error = null
      }
    }

    while (!eof()) {
      val tt = token()
      when {
        tt === XML_START_TAG_START -> {
          flushError()
          rootTagCount++
          parseTag(rootTagCount > 1)
        }
        isCommentToken(tt) -> {
          flushError()
          parseComment()
        }
        tt === XML_PI_START -> {
          flushError()
          parseProcessingInstruction()
        }
        tt === XML_REAL_WHITE_SPACE -> {
          flushError()
          advance()
        }
        else -> {
          if (error == null) error = mark()
          advance()
        }
      }
    }

    error?.error(message("xml.parsing.top.level.element.is.not.completed"))

    if (rootTagCount == 0) {
      val rootTag = mark()
      mark().error(message("xml.parsing.absent.root.tag"))
      rootTag.done(XML_TAG)
    }

    document.done(XML_DOCUMENT)
  }

  private fun parseDoctype() {
    checkCurrentToken(XML_DOCTYPE_START) { "Doctype start expected" }
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
    checkCurrentToken(XML_START_TAG_START) { "Tag start expected" }
    val tag = mark()

    val tagName = parseTagHeader(multipleRootTagError, tag) ?: return

    val content = mark()
    parseTagContent()

    if (token() === XML_END_TAG_START) {
      val footer = mark()
      advance()

      if (token() === XML_NAME) {
        val endName = myBuilder.tokenText
        if (tagName != endName && endName in tagNamesStack) {
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

    while (true) {
      val tt = token()
      when {
        tt === XML_NAME -> parseAttribute()
        tt === XML_CHAR_ENTITY_REF || tt === XML_ENTITY_REF_TOKEN -> parseReference()
        else -> break
      }
    }

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

    fun terminateText() {
      xmlText?.let {
        it.done(XML_TEXT)
        xmlText = null
      }
    }

    fun startText() {
      if (xmlText == null) xmlText = mark()
    }

    while (true) {
      val tt = token()
      if (tt == null || tt === XML_END_TAG_START) {
        break
      }

      if (tt === XML_START_TAG_START) {
        terminateText()
        parseTag(false)
      }
      else if (tt === XML_PI_START) {
        terminateText()
        parseProcessingInstruction()
      }
      else if (tt === XML_ENTITY_REF_TOKEN) {
        terminateText()
        parseReference()
      }
      else if (tt === XML_CHAR_ENTITY_REF) {
        startText()
        parseReference()
      }
      else if (tt === XML_CDATA_START) {
        startText()
        parseCData()
      }
      else if (isCommentToken(tt)) {
        terminateText()
        parseComment()
      }
      else if (tt === XML_BAD_CHARACTER) {
        startText()
        val error = mark()
        advance()
        error.error(message("xml.parsing.unescaped.ampersand.or.nonterminated.character.entity.reference"))
      }
      else if (tt is ICustomParsingType || tt is ILazyParseableElementType) {
        terminateText()
        advance()
      }
      else {
        startText()
        advance()
      }
    }

    terminateText()
  }

  protected open fun isCommentToken(tt: IElementType?): Boolean {
    return tt === XML_COMMENT_START
  }

  protected fun mark(): PsiBuilder.Marker {
    return myBuilder.mark()
  }

  private fun parseCData() {
    checkCurrentToken(XML_CDATA_START)
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
    val tt = token()
    when {
      tt === XML_CHAR_ENTITY_REF -> {
        advance()
      }
      tt === XML_ENTITY_REF_TOKEN -> {
        val ref = mark()
        advance()
        ref.done(XML_ENTITY_REF)
      }
      else -> {
        kotlin.error("Unexpected token: $tt")
      }
    }
  }

  private fun parseAttribute() {
    checkCurrentToken(XML_NAME)
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
      when {
        tt === XML_PI_START -> parseProcessingInstruction()
        tt === XML_DOCTYPE_START -> parseDoctype()
        isCommentToken(tt) -> parseComment()
        tt === XML_REAL_WHITE_SPACE -> advance()
        else -> break
      }
    }
    prolog.done(XML_PROLOG)
  }

  private fun parseProcessingInstruction() {
    checkCurrentToken(XML_PI_START)
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

  private fun checkCurrentToken(type: IElementType) {
    check(token() === type) { "Expected: $type, got: ${token()}" }
  }

  private inline fun checkCurrentToken(type: IElementType, error: () -> String) {
    check(token() === type, error)
  }
}

private const val BALANCING_DEPTH_THRESHOLD = 1000

