// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.python.requirements.parser.RequirementsLexerAdapter


internal class RequirementsSyntaxHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer {
    return RequirementsLexerAdapter()
  }

  override fun getTokenHighlights(tokenType: IElementType): Array<out TextAttributesKey> {
    return when (tokenType) {
      TokenType.BAD_CHARACTER -> BAD_CHAR_KEYS
      com.intellij.python.requirements.parser.psi.RequirementsTypes.AND -> OPERATOR_KEYS
      com.intellij.python.requirements.parser.psi.RequirementsTypes.COMMA -> COMMA_KEYS
      com.intellij.python.requirements.parser.psi.RequirementsTypes.COMMENT -> COMMENT_KEYS
      com.intellij.python.requirements.parser.psi.RequirementsTypes.ENV_MARKER_NAME -> ENV_MARKER_NAME_KEYS
      com.intellij.python.requirements.parser.psi.RequirementsTypes.LSBRACE -> BRACE_KEYS
      com.intellij.python.requirements.parser.psi.RequirementsTypes.IN_OP -> OPERATOR_KEYS
      com.intellij.python.requirements.parser.psi.RequirementsTypes.NOTIN_OP -> OPERATOR_KEYS
      com.intellij.python.requirements.parser.psi.RequirementsTypes.OR -> OPERATOR_KEYS
      com.intellij.python.requirements.parser.psi.RequirementsTypes.PACKAGE_NAME_TOKEN -> PACKAGE_NAME_KEYS
      com.intellij.python.requirements.parser.psi.RequirementsTypes.QUOTED_STRING_TOKEN -> QUOTED_STRING_KEYS
      com.intellij.python.requirements.parser.psi.RequirementsTypes.RSBRACE -> BRACE_KEYS
      com.intellij.python.requirements.parser.psi.RequirementsTypes.SEMICOLON -> SEMICOLON_KEYS
      com.intellij.python.requirements.parser.psi.RequirementsTypes.VERSION_TOKEN -> VERSION_KEYS
      else -> EMPTY_KEYS
    }
  }

  companion object {
    val BAD_CHARACTER = createTextAttributesKey(
      "REQUIREMENTS_BAD_CHARACTER",
      HighlighterColors.BAD_CHARACTER
    )
    val COMMA = createTextAttributesKey(
      "REQUIREMENTS_COMMA",
      DefaultLanguageHighlighterColors.COMMA
    )
    val COMMENT = createTextAttributesKey(
      "REQUIREMENTS_COMMENT",
      DefaultLanguageHighlighterColors.LINE_COMMENT
    )
    val ENV_MARKER_NAME = createTextAttributesKey(
      "REQUIREMENTS_ENV_MARKER_NAME",
      DefaultLanguageHighlighterColors.IDENTIFIER
    )
    val PACKAGE_NAME = createTextAttributesKey(
      "REQUIREMENTS_PACKAGE_NAME",
      DefaultLanguageHighlighterColors.KEYWORD
    )
    val QUOTED_STRING = createTextAttributesKey(
      "REQUIREMENTS_QUOTED_STRING",
      DefaultLanguageHighlighterColors.STRING
    )
    val VERSION = createTextAttributesKey(
      "REQUIREMENTS_VERSION",
      DefaultLanguageHighlighterColors.STRING
    )
    val OPERATOR = createTextAttributesKey(
      "REQUIREMENTS_OPERATOR",
      DefaultLanguageHighlighterColors.KEYWORD
    )
    val SEMICOLON = createTextAttributesKey(
      "REQUIREMENTS_SEMICOLON",
      DefaultLanguageHighlighterColors.SEMICOLON
    )

    private val BAD_CHAR_KEYS = arrayOf(BAD_CHARACTER)
    private val BRACE_KEYS = arrayOf(COMMENT)
    private val COMMENT_KEYS = arrayOf(COMMENT)
    private val COMMA_KEYS = arrayOf(COMMA)
    private val EMPTY_KEYS = emptyArray<TextAttributesKey>()
    private val ENV_MARKER_NAME_KEYS = arrayOf(ENV_MARKER_NAME)
    private val PACKAGE_NAME_KEYS = arrayOf(PACKAGE_NAME)
    private val OPERATOR_KEYS = arrayOf(OPERATOR)
    private val QUOTED_STRING_KEYS = arrayOf(QUOTED_STRING)
    private val SEMICOLON_KEYS = arrayOf(SEMICOLON)
    private val VERSION_KEYS = arrayOf(VERSION)
  }
}