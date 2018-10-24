// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.lexer

import com.intellij.util.text.CharArrayUtil
import com.jetbrains.python.PyTokenTypes

class PyFStringLiteralLexer: PyStringLiteralLexerBase(PyTokenTypes.FSTRING_TEXT) {
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

  // TODO actually keep track of "raw" prefixes of f-strings somehow
  override fun isRaw(): Boolean = false

  override fun isUnicodeMode(): Boolean = true

  override fun getState(): Int = myBaseLexerState
}
