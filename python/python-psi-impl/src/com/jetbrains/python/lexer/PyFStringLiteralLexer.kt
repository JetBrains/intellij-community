// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.lexer

import com.intellij.psi.tree.IElementType
import com.intellij.util.text.CharArrayUtil
import com.jetbrains.python.PyTokenTypes

class PyFStringLiteralLexer(fStringTextToken: IElementType) : PyStringLiteralLexerBase(fStringTextToken) {
  init {
    assert(PyTokenTypes.FSTRING_TEXT_TOKENS.contains(fStringTextToken))
  }

  override fun locateToken(start: Int): Int {
    if (start >= myBufferEnd) {
      return myBufferEnd
    }
    
    if (myBuffer[start] == '\\') {
      return locateEscapeSequence(start)
    }
    else {
      val nextBackslashOffset = CharArrayUtil.indexOf(myBuffer, "\\", start + 1, myBufferEnd)
      return if (nextBackslashOffset >= 0) nextBackslashOffset else myBufferEnd
    }
  }

  override fun isRaw(): Boolean = myOriginalLiteralToken == PyTokenTypes.FSTRING_RAW_TEXT

  override fun isUnicodeMode(): Boolean = true

  override fun getState(): Int = myBaseLexerState
}
