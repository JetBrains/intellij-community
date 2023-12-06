// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.jetbrains.python.requirements.lexer.RequirementsLexerAdapter
import com.jetbrains.python.requirements.psi.RequirementsTypes


class RequirementsSyntaxHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer {
    return RequirementsLexerAdapter()
  }

  override fun getTokenHighlights(tokenType: IElementType): Array<out TextAttributesKey?> {
    return when (tokenType) {
      TokenType.BAD_CHARACTER -> BAD_CHAR_KEYS
      RequirementsTypes.AND -> OPERATOR_KEYS
      RequirementsTypes.COMMENT -> COMMENT_KEYS
      RequirementsTypes.CONSTRAINT -> IDENTIFIER_KEYS
      RequirementsTypes.EDITABLE -> IDENTIFIER_KEYS
      RequirementsTypes.ENV_VAR -> ENV_VAR_KEYS
      RequirementsTypes.EXTRA_INDEX_URL -> IDENTIFIER_KEYS
      RequirementsTypes.FIND_LINKS -> IDENTIFIER_KEYS
      RequirementsTypes.IDENTIFIER -> IDENTIFIER_KEYS
      RequirementsTypes.IN -> OPERATOR_KEYS
      RequirementsTypes.INDEX_URL -> URL_KEYS
      RequirementsTypes.NO_BINARY -> IDENTIFIER_KEYS
      RequirementsTypes.NO_INDEX -> IDENTIFIER_KEYS
      RequirementsTypes.NOT -> OPERATOR_KEYS
      RequirementsTypes.ONLY_BINARY -> IDENTIFIER_KEYS
      RequirementsTypes.OR -> OPERATOR_KEYS
      RequirementsTypes.PRE -> IDENTIFIER_KEYS
      RequirementsTypes.PREFER_BINARY -> IDENTIFIER_KEYS
      RequirementsTypes.PYTHON_STR_C -> PYTHON_STR_C_KEYS
      RequirementsTypes.REFER -> IDENTIFIER_KEYS
      RequirementsTypes.REQUIRE_HASHES -> IDENTIFIER_KEYS
      RequirementsTypes.SEMICOLON -> SEMICOLON_KEYS
      RequirementsTypes.SHARP -> SHARP_KEYS
      RequirementsTypes.TRUSTED_HOST -> IDENTIFIER_KEYS
      RequirementsTypes.USE_FEATURE -> IDENTIFIER_KEYS
      RequirementsTypes.VERSION -> VERSION_KEYS
      else -> EMPTY_KEYS
    }
  }

  companion object {
    val BAD_CHARACTER = createTextAttributesKey(
      "REQUIREMENTS_BAD_CHARACTER",
      HighlighterColors.BAD_CHARACTER
    )
    val COMMENT = createTextAttributesKey(
      "REQUIREMENTS_COMMENT",
      DefaultLanguageHighlighterColors.LINE_COMMENT
    )
    val ENV_VAR = createTextAttributesKey(
      "REQUIREMENTS_ENV_VAR",
      DefaultLanguageHighlighterColors.STRING
    )
    val IDENTIFIER = createTextAttributesKey(
      "IDENTIFIER",
      DefaultLanguageHighlighterColors.KEYWORD
    )
    val PYTHON_STR_C = createTextAttributesKey(
      "REQUIREMENTS_PYTHON_STR_C",
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
    val SHARP = createTextAttributesKey(
      "REQUIREMENTS_SHARP",
      DefaultLanguageHighlighterColors.SEMICOLON
    )
    val SEMICOLON = createTextAttributesKey(
      "REQUIREMENTS_SEMICOLON",
      DefaultLanguageHighlighterColors.SEMICOLON
    )
    val URL = createTextAttributesKey(
      "REQUIREMENTS_URL",
      DefaultLanguageHighlighterColors.STRING
    )

    private val BAD_CHAR_KEYS = arrayOf(BAD_CHARACTER)
    private val COMMENT_KEYS = arrayOf(COMMENT)
    private val EMPTY_KEYS = emptyArray<TextAttributesKey>()
    private val ENV_VAR_KEYS = arrayOf(ENV_VAR)
    private val IDENTIFIER_KEYS = arrayOf(IDENTIFIER)
    private val OPERATOR_KEYS = arrayOf(OPERATOR)
    private val PYTHON_STR_C_KEYS = arrayOf(PYTHON_STR_C)
    private val SEMICOLON_KEYS = arrayOf(SEMICOLON)
    private val SHARP_KEYS = arrayOf(SHARP)
    private val VERSION_KEYS = arrayOf(VERSION)
    private val URL_KEYS = arrayOf(URL)
  }
}