// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.lexer.PythonLexer

/**
 * @author Mikhail Golubev
 */
object PyStringLiteralUtil : PyStringLiteralCoreUtil() {
  private val LOG = thisLogger()

  /**
   * 'text' => text
   * "text" => text
   * text => text
   * "text => "text
   *
   * @return string without heading and trailing pair of ' or "
   */
  @JvmStatic
  fun getStringValue(s: String): String {
    return getStringValueTextRange(s).substring(s)
  }

  @JvmStatic
  fun getStringValueTextRange(s: String): TextRange {
    val quotes = getQuotes(s)
    if (quotes != null) {
      return TextRange.create(quotes.getFirst()!!.length, s.length - quotes.getSecond()!!.length)
    }
    return TextRange.allOf(s)
  }

  /**
   * @return whether the given text is recognized as a valid string literal token by Python lexer
   */
  @JvmStatic
  fun isStringLiteralToken(text: String): Boolean {
    val lexer = PythonLexer()
    lexer.start(text)
    return PyTokenTypes.STRING_NODES.contains(lexer.tokenType) && lexer.tokenEnd == lexer.bufferEnd ||
           PyTokenTypes.FSTRING_START === lexer.tokenType
  }

  /**
   * Returns the range of the string literal text between the opening quote and the closing one.
   * If the closing quote is either missing or mismatched, this range spans until the end of the literal.
   */
  @JvmStatic
  fun getContentRange(text: String): TextRange {
    LOG.assertTrue(isStringLiteralToken(text), "Text of a single string literal node expected")
    var startOffset = getPrefixLength(text)
    var delimiterLength = 1
    val afterPrefix = text.substring(startOffset)
    if (afterPrefix.startsWith("\"\"\"") || afterPrefix.startsWith("'''")) {
      delimiterLength = 3
    }
    val delimiter = text.substring(startOffset, startOffset + delimiterLength)
    startOffset += delimiterLength
    var endOffset = text.length
    if (text.substring(startOffset).endsWith(delimiter)) {
      endOffset -= delimiterLength
    }
    return TextRange(startOffset, endOffset)
  }

  @JvmStatic
  fun getPrefixLength(text: String): Int {
    return getPrefixEndOffset(text, 0)
  }

  /**
   * @return whether the given prefix contains either 'u' or 'U' character
   */
  @JvmStatic
  fun isUnicodePrefix(prefix: String): Boolean {
    return StringUtil.indexOfIgnoreCase(prefix, 'u', 0) >= 0
  }

  /**
   * @return whether the given prefix contains either 'b' or 'B' character
   */
  @JvmStatic
  fun isBytesPrefix(prefix: String): Boolean {
    return StringUtil.indexOfIgnoreCase(prefix, 'b', 0) >= 0
  }

  /**
   * @return whether the given prefix contains either 'r' or 'R' character
   */
  @JvmStatic
  fun isRawPrefix(prefix: String): Boolean {
    return StringUtil.indexOfIgnoreCase(prefix, 'r', 0) >= 0
  }

  /**
   * @return whether the given prefix contains either 'f' or 'F' character
   */
  @JvmStatic
  fun isFormattedPrefix(prefix: String): Boolean {
    return StringUtil.indexOfIgnoreCase(prefix, 'f', 0) >= 0
  }

  /**
   * @return whether the given prefix contains either 't' or 'T' character
   */
  @JvmStatic
  fun isTemplatePrefix(prefix: String): Boolean {
    return StringUtil.indexOfIgnoreCase(prefix, 't', 0) >= 0
  }

  /**
   * @return alternative quote character, i.e. " for ' and ' for "
   */
  @JvmStatic
  fun flipQuote(quote: Char): Char {
    return if (quote == '"') '\'' else '"'
  }
}
