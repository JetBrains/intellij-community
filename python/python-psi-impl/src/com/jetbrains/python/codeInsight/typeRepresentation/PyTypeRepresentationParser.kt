/*
 * Copyright 2000-2025 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight.typeRepresentation

import com.intellij.lang.SyntaxTreeBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.tree.IElementType
import com.jetbrains.python.PyElementTypes
import com.jetbrains.python.PyParsingBundle
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.parsing.ExpressionParsing
import com.jetbrains.python.parsing.ParsingContext
import com.jetbrains.python.parsing.PyParser
import com.jetbrains.python.parsing.StatementParsing
import com.jetbrains.python.psi.LanguageLevel

class PyTypeRepresentationParser : PyParser() {
  override fun createParsingContext(builder: SyntaxTreeBuilder?, languageLevel: LanguageLevel?): ParsingContext {
    return object : ParsingContext(builder, languageLevel) {
      private val myExpressionParsing = TypeRepresentationParser(this)
      private val myStatementParsing = object : StatementParsing(this) {
        override fun parseStatement() {
          expressionParser.parseExpression()
        }
      }

      override fun getExpressionParser(): ExpressionParsing = myExpressionParsing
      override fun getStatementParser(): StatementParsing = myStatementParsing
    }
  }

  private class TypeRepresentationParser(context: ParsingContext?) : ExpressionParsing(context) {
    override fun parseExpression() {
      if (myBuilder.eof()) {
        return
      }
      // Skip statement breaks - they're not part of type expressions
      if (atToken(PyTokenTypes.STATEMENT_BREAK)) {
        myBuilder.advanceLexer()
        return
      }

      val startOffset = myBuilder.currentOffset

      // Handle @Todo(...) annotations for unsupported type constructs
      if (atToken(PyTokenTypes.AT)) {
        parseTodoAnnotation()
        return
      }

      // Try to parse function type (callable syntax) first
      if (!parseFunctionType()) {
        // Not a function type, parse as regular expression
        super.parseSingleExpression(false)
        // Ensure we made progress
        if (myBuilder.currentOffset == startOffset && !myBuilder.eof()) {
          // We didn't consume anything, advance to avoid infinite loop
          myBuilder.error("Unexpected token") // NON-NLS
          myBuilder.advanceLexer()
        }
      }
    }

    /**
     * Parses @Todo(...) style annotations used to mark unsupported type constructs.
     * These are opaque markers and their content is not interpreted.
     */
    private fun parseTodoAnnotation() {
      assert(atToken(PyTokenTypes.AT))
      val annotationMarker = myBuilder.mark()
      myBuilder.advanceLexer() // consume @

      // Consume tokens until we hit a structural boundary
      var depth = 0
      while (!myBuilder.eof()) {
        val token = myBuilder.getTokenType()
        when {
          token == PyTokenTypes.LPAR -> {
            depth++
            myBuilder.advanceLexer()
          }
          token == PyTokenTypes.RPAR -> {
            if (depth == 0) break // Structural boundary (parameter list close, etc.)
            depth--
            myBuilder.advanceLexer()
          }
          token == PyTokenTypes.COMMA && depth == 0 -> break // Structural boundary
          token == PyTokenTypes.RARROW && depth == 0 -> break // Structural boundary (->)
          token == PyTokenTypes.STATEMENT_BREAK -> break // Structural boundary
          else -> myBuilder.advanceLexer()
        }
      }
      annotationMarker.done(PyTypeRepresentationElementTypes.PLACEHOLDER)
    }

    fun parseFunctionType(): Boolean {
      val mark = myBuilder.mark()

      // Parse 'def' types: `def foo.bar(a: int) -> str`
      if (atToken(PyTokenTypes.DEF_KEYWORD)) {
        myBuilder.advanceLexer() // consume 'def'

        // Parse dotted name (e.g., foo.bar.baz)
        if (!atToken(PyTokenTypes.IDENTIFIER)) {
          mark.rollbackTo()
          return false
        }

        val nameMarker = myBuilder.mark()
        myBuilder.advanceLexer() // consume first identifier

        // Parse remaining dotted parts
        while (atToken(PyTokenTypes.DOT)) {
          myBuilder.advanceLexer() // consume dot
          if (!atToken(PyTokenTypes.IDENTIFIER)) {
            mark.rollbackTo()
            return false
          }
          myBuilder.advanceLexer() // consume identifier
        }
        nameMarker.done(PyElementTypes.REFERENCE_EXPRESSION)
      }

      // TODO: type parameters list `[T: int = int, *Ts, **P]`

      if (atToken(PyTokenTypes.LPAR)) {
        parseParameterTypeList()
        // Check if this is a function type (has ->) or just a parenthesized expression/tuple
        if (atToken(PyTokenTypes.RARROW)) {
          myBuilder.advanceLexer() // consume ->
          // Use parseExpression to handle nested function types in return position
          parseExpression()
          mark.done(PyTypeRepresentationElementTypes.FUNCTION_SIGNATURE)
          return true
        }
      }
      // Not a function type, rollback
      mark.rollbackTo()
      return false
    }

    fun parseParameterTypeList() {
      assert(atToken(PyTokenTypes.LPAR))
      val listMark = myBuilder.mark()
      myBuilder.advanceLexer()

      var paramCount = 0
      while (!(atAnyOfTokens(PyTokenTypes.RPAR, PyTokenTypes.RARROW, PyTokenTypes.STATEMENT_BREAK) || myBuilder.eof())) {
        val currentOffset = myBuilder.currentOffset

        if (paramCount > 0) {
          if (!checkMatches(PyTokenTypes.COMMA, PyParsingBundle.message("PARSE.expected.comma"))) {
            // No comma found, break out of parameter parsing
            break
          }
        }
        val parsed: Boolean
        if (atToken(PyTokenTypes.DIV)) {
          // Handle positional-only separator /
          val slashMarker = myBuilder.mark()
          myBuilder.advanceLexer()
          slashMarker.done(PyElementTypes.SLASH_PARAMETER)
          parsed = true
        }
        else if (atToken(PyTokenTypes.MULT)) {
          val starMarker = myBuilder.mark()
          myBuilder.advanceLexer()
          // Check if this is a named *args parameter (*name: type)
          if (atToken(PyTokenTypes.IDENTIFIER) && myBuilder.lookAhead(1) == PyTokenTypes.COLON) {
            val namedVarargMarker = myBuilder.mark()
            myBuilder.advanceLexer() // consume identifier
            myBuilder.advanceLexer() // consume colon
            // Parse the type expression, handling * prefix
            if (atToken(PyTokenTypes.MULT)) {
              val innerStarMarker = myBuilder.mark()
              myBuilder.advanceLexer()
              parseExpression()
              innerStarMarker.done(PyElementTypes.STAR_EXPRESSION)
            }
            else {
              parseExpression()
            }
            namedVarargMarker.done(PyTypeRepresentationElementTypes.NAMED_PARAMETER_TYPE)
          }
          else {
            // Unnamed *args, just parse the type
            parseExpression()
          }
          parsed = true
          starMarker.done(PyElementTypes.STAR_EXPRESSION)
        }
        else if (atToken(PyTokenTypes.EXP)) {
          val doubleStarMarker = myBuilder.mark()
          myBuilder.advanceLexer()
          // Check if this is a named **kwargs parameter (**name: type)
          if (atToken(PyTokenTypes.IDENTIFIER) && myBuilder.lookAhead(1) == PyTokenTypes.COLON) {
            val namedKwargMarker = myBuilder.mark()
            myBuilder.advanceLexer() // consume identifier
            myBuilder.advanceLexer() // consume colon
            // Parse the type expression, handling ** prefix
            if (atToken(PyTokenTypes.EXP)) {
              val innerDoubleStarMarker = myBuilder.mark()
              myBuilder.advanceLexer()
              parseExpression()
              innerDoubleStarMarker.done(PyElementTypes.DOUBLE_STAR_EXPRESSION)
            }
            else {
              parseExpression()
            }
            namedKwargMarker.done(PyTypeRepresentationElementTypes.NAMED_PARAMETER_TYPE)
          }
          else {
            // Unnamed **kwargs, just parse the type
            parseExpression()
          }
          parsed = true
          doubleStarMarker.done(PyElementTypes.DOUBLE_STAR_EXPRESSION)
        }
        else {
          if (atToken(PyTokenTypes.AT)) {
            parseTodoAnnotation()
            parsed = true
          }
          // Check if this is a named parameter (name: type) or (name: type = default)
          else if (atToken(PyTokenTypes.IDENTIFIER) && myBuilder.lookAhead(1) == PyTokenTypes.COLON) {
            val namedParamMarker = myBuilder.mark()
            myBuilder.advanceLexer() // consume identifier
            myBuilder.advanceLexer() // consume colon
            // Parse the type expression
            parseExpression()
            // Check for default value
            if (atToken(PyTokenTypes.EQ)) {
              myBuilder.advanceLexer() // consume =
              parseExpression() // parse default value
            }
            namedParamMarker.done(PyTypeRepresentationElementTypes.NAMED_PARAMETER_TYPE)
            parsed = myBuilder.currentOffset > currentOffset
          }
          else {
            // Use parseExpression to handle nested function types and positional types
            parseExpression()
            parsed = myBuilder.currentOffset > currentOffset // Check we made progress
          }
        }
        if (!parsed) {
          myBuilder.error(PyParsingBundle.message("PARSE.expected.expression"))
          recoverUntilMatches(PyParsingBundle.message("PARSE.expected.expression"), PyTokenTypes.COMMA, PyTokenTypes.RPAR,
                              PyTokenTypes.RARROW, PyTokenTypes.STATEMENT_BREAK)
        }
        paramCount++

      }
      checkMatches(PyTokenTypes.RPAR, PyParsingBundle.message("PARSE.expected.rpar"))
      listMark.done(PyTypeRepresentationElementTypes.PARAMETER_TYPE_LIST)
    }

    fun recoverUntilMatches(errorMessage: @NlsContexts.ParsingError String, vararg types: IElementType) {
      val errorMarker = myBuilder.mark()
      var hasNonWhitespaceTokens = false
      while (!(atAnyOfTokens(*types) || myBuilder.eof())) {
        // Regular whitespace tokens are already skipped by advancedLexer() 
        if (!atToken(PyTokenTypes.STATEMENT_BREAK)) {
          hasNonWhitespaceTokens = true
        }
        myBuilder.advanceLexer()
      }
      if (hasNonWhitespaceTokens) {
        errorMarker.error(errorMessage)
      }
      else {
        errorMarker.drop()
      }
    }
  }
}
