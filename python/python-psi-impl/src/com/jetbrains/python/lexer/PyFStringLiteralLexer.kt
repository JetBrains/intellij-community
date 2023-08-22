// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.lexer

import com.intellij.psi.StringEscapesTokenTypes
import com.intellij.psi.tree.IElementType
import com.intellij.util.text.CharArrayUtil
import com.jetbrains.python.PyTokenTypes

class PyFStringLiteralLexer(fStringTextToken: IElementType) : PyStringLiteralLexerBase(fStringTextToken) {
  init {
    assert(PyTokenTypes.FSTRING_TEXT_TOKENS.contains(fStringTextToken))
  }

  override fun locateToken(start: Int): Int = when {
    start >= myBufferEnd -> myBufferEnd
    myBuffer[start] == '\\' -> locateEscapeSequence(start)
    isDoubleCurley() -> start + 2
    else -> {
      val nextBackslashOffset = CharArrayUtil.indexOf(myBuffer, "\\", start + 1, myBufferEnd)
      val nextLCurleyOffset = CharArrayUtil.indexOf(myBuffer, "{{", start + 1, myBufferEnd)
      val nextRCurleyOffset = CharArrayUtil.indexOf(myBuffer, "}}", start + 1, myBufferEnd)
      setOf(nextBackslashOffset, nextLCurleyOffset, nextRCurleyOffset).filter { it >= 0 }.minOrNull() ?: myBufferEnd
    }
  }

  override fun isRaw(): Boolean = myOriginalLiteralToken == PyTokenTypes.FSTRING_RAW_TEXT

  override fun isUnicodeMode(): Boolean = true

  override fun getState(): Int = myBaseLexerState

  override fun isEscape() = isDoubleCurley() || super.isEscape()

  override fun getEscapeSequenceType(): IElementType =
    if (isDoubleCurley()) StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN else super.getEscapeSequenceType()

  private fun isDoubleCurley() =
    myBufferEnd > myStart + 1 &&
    ((myBuffer[myStart] == '{' && myBuffer[myStart + 1] == '{') || (myBuffer[myStart] == '}' && myBuffer[myStart + 1] == '}'))
}
