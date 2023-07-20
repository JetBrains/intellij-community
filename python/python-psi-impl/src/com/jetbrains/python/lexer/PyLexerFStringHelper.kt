// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.lexer

import com.intellij.psi.tree.IElementType
import com.intellij.util.containers.Stack
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyElementType
import com.jetbrains.python.psi.PyStringLiteralUtil
import kotlin.math.min


class PyLexerFStringHelper(private val myLexer: FlexLexerEx) {
  private val myFStringStates: Stack<FStringState> = Stack()

  fun handleFStringStartInFragment(): IElementType {
    val prefixAndQuotes = myLexer.yytext().toString()
    val (_, offset) = findFStringTerminator(prefixAndQuotes)
    if (offset == prefixAndQuotes.length) {
      return pushFString(prefixAndQuotes)
    }
    return PyTokenTypes.IDENTIFIER
  }

  fun handleFStringStart(): IElementType {
    return pushFString(myLexer.yytext().toString())
  }

  private fun pushFString(prefixAndQuotes: String): PyElementType {
    val prefixLength = PyStringLiteralUtil.getPrefixLength(prefixAndQuotes)
    val openingQuotes = prefixAndQuotes.substring(prefixLength)
    val prefix = prefixAndQuotes.substring(0, prefixLength)
    myFStringStates.push(FStringState(myLexer.yystate(), myLexer.tokenStart, prefix, openingQuotes))
    myLexer.yybegin(_PythonLexer.FSTRING)
    return PyTokenTypes.FSTRING_START
  }

  fun handleFStringEnd(): IElementType {
    val textType = getTextTokenType()
    val (type, offset) = findFStringTerminator(myLexer.yytext().toString())
    return if (offset == 0) type!! else textType
  }

  fun handleFragmentStart(): IElementType {
    myFStringStates.peek().fragmentStates.push(FragmentState(myLexer.yystate(), myLexer.tokenStart))
    myLexer.yybegin(_PythonLexer.FSTRING_FRAGMENT)
    return PyTokenTypes.FSTRING_FRAGMENT_START
  }

  fun handleFragmentEnd(): IElementType {
    val state = myFStringStates.peek().fragmentStates.pop()
    myLexer.yybegin(state.oldState)
    return PyTokenTypes.FSTRING_FRAGMENT_END
  }

  fun handleLeftBracketInFragment(type: IElementType): IElementType {
    myFStringStates.peek().fragmentStates.peek().braceBalance++
    return type
  }

  fun handleRightBracketInFragment(type: IElementType): IElementType {
    val activeFragmentState = myFStringStates.peek().fragmentStates.peek()
    if (activeFragmentState.braceBalance == 0 && type == PyTokenTypes.RBRACE) {
      return handleFragmentEnd()
    }
    else {
      activeFragmentState.braceBalance--
      return type
    }
  }

  fun handleColonInFragment(): IElementType {
    if (myFStringStates.peek().fragmentStates.peek().braceBalance == 0) {
      myLexer.yybegin(_PythonLexer.FSTRING_FRAGMENT_FORMAT)
      return PyTokenTypes.FSTRING_FRAGMENT_FORMAT_START
    }
    else {
      return PyTokenTypes.COLON
    }
  }

  fun handleColonEqInFragment(): IElementType {
    if (myFStringStates.peek().fragmentStates.peek().braceBalance == 0) {
      myLexer.yybegin(_PythonLexer.FSTRING_FRAGMENT_FORMAT)
      myLexer.yypushback(1)
      return PyTokenTypes.FSTRING_FRAGMENT_FORMAT_START
    }
    else {
      return PyTokenTypes.COLONEQ
    }
  }

  fun handleLineBreakInFragment(): IElementType {
    val text = myLexer.yytext().toString()
    // We will return a line break anyway, but we need to transit from FSTRING state of the lexer
    findFStringTerminator(text)
    return PyTokenTypes.LINE_BREAK
  }

  fun handleLineBreakInLiteralText(): IElementType {
    val text = myLexer.yytext().toString()
    val (_, offset) = findFStringTerminator(text)
    if (offset == text.length) {
      return getTextTokenType()
    }
    return PyTokenTypes.LINE_BREAK
  }

  fun handleStringLiteral(stringLiteralType: IElementType): IElementType {
    val stringText = myLexer.yytext().toString()
    val prefixLength = PyStringLiteralUtil.getPrefixLength(stringText)

    val (type, offset) = findFStringTerminator(stringText)
    return when (offset) {
      0 -> type!!
      prefixLength -> PyTokenTypes.IDENTIFIER
      else -> stringLiteralType
    }
  }

  private fun findFStringTerminator(text: String): Pair<IElementType?, Int> {
    var i = 0
    while (i < text.length) {
      val c = text[i]
      if (c == '\\') {
        i += 2
        continue
      }
      if (c == '\n') {
        val insideSingleQuoted = myFStringStates.any { it.openingQuotes.length == 1 }
        if (insideSingleQuoted) {
          if (i == 0) {
            // Terminate all f-strings and insert STATEMENT_BREAK at this point
            dropFStringStateWithAllNested(0)
          }
          pushBackToOrConsumeMatch(i, 1)
          return Pair(PyTokenTypes.LINE_BREAK, i)
        }
      }
      else {
        val nextThree = text.substring(i, min(text.length, i + 3))
        val lastWithMatchingQuotesIndex = myFStringStates.indexOfLast { nextThree.startsWith(it.openingQuotes) }
        if (lastWithMatchingQuotesIndex >= 0) {
          val state = myFStringStates[lastWithMatchingQuotesIndex]
          if (i == 0) {
            dropFStringStateWithAllNested(lastWithMatchingQuotesIndex)
          }
          pushBackToOrConsumeMatch(i, state.openingQuotes.length)
          return Pair(PyTokenTypes.FSTRING_END, i)
        }
      }
      i++
    }
    return Pair(null, text.length)
  }

  private fun dropFStringStateWithAllNested(fStringIndex: Int) {
    myLexer.yybegin(myFStringStates[fStringIndex].oldState)
    myFStringStates.subList(fStringIndex, myFStringStates.size).clear()
  }

  private fun pushBackToOrConsumeMatch(matchOffset: Int, matchSize: Int) {
    if (matchOffset == 0) {
      myLexer.yypushback(myLexer.yylength() - matchSize)
    }
    else {
      myLexer.yypushback(myLexer.yylength() - matchOffset)
    }
  }

  fun reset() {
    // There is no need to be smarter about it, since LexerEditorHighlighter always resets 
    // the lexer state to YYINITIAL where there can't be any f-strings.
    myFStringStates.clear()
  }

  fun getTextTokenType(): IElementType {
    assert(myFStringStates.isNotEmpty())
    if (PyStringLiteralUtil.isRawPrefix(myFStringStates.peek().prefix)) {
      return PyTokenTypes.FSTRING_RAW_TEXT
    }
    return PyTokenTypes.FSTRING_TEXT
  }

  private data class FStringState(val oldState: Int,
                                  val offset: Int,
                                  val prefix: String,
                                  val openingQuotes: String) {
    val fragmentStates = Stack<FragmentState>()
  }

  private data class FragmentState(val oldState: Int, val offset: Int) {
    var braceBalance: Int = 0
  }
}
