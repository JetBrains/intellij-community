// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.lexer

import com.intellij.psi.tree.IElementType
import com.intellij.util.containers.Stack
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyStringLiteralUtil



class PyLexerFStringHelper(private val myLexer: FlexLexerEx) {
  private val myFStringStates: Stack<FStringState> = Stack()

  fun handleFStringStart(): IElementType {
    val prefixAndQuotes = myLexer.yytext().toString()
    val (_, offset) = findFStringTerminator(prefixAndQuotes)
    if (offset == prefixAndQuotes.length) {
      val openingQuotes = prefixAndQuotes.substring(PyStringLiteralUtil.getPrefixLength(prefixAndQuotes))
      myFStringStates.push(FStringState(myLexer.yystate(), openingQuotes))
      myLexer.yybegin(_PythonLexer.FSTRING)
      return PyTokenTypes.FSTRING_START
    }
    return PyTokenTypes.IDENTIFIER
  }

  fun handleFStringEnd(): IElementType {
    val (type, offset) = findFStringTerminator(myLexer.yytext().toString())
    return if (offset == 0) type!! else PyTokenTypes.FSTRING_TEXT
  }

  fun handleFragmentStart(): IElementType {
    myFStringStates.peek().fragmentStates.push(FragmentState(myLexer.yystate()))
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
      val nextThree = text.substring(i, Math.min(text.length, i + 3))
      for (j in myFStringStates.size - 1 downTo 0) {
        val state = myFStringStates[j]
        if (nextThree.startsWith(state.openingQuotes)) {
          if (i == 0) {
            myFStringStates.subList(i, myFStringStates.size).clear()
            myLexer.yybegin(state.oldState)
            val unmatched = text.length - state.openingQuotes.length
            myLexer.yypushback(unmatched)
          }
          else {
            myLexer.yypushback(text.length - i)
          }
          return Pair(PyTokenTypes.FSTRING_END, i)
        }
      }
      i++
    }
    return Pair(null, text.length)
  }


  private data class FStringState(val oldState: Int, val openingQuotes: String) {
    val fragmentStates = Stack<FragmentState>()
  }

  private data class FragmentState(val oldState: Int) {
    var braceBalance: Int = 0
  }
}
