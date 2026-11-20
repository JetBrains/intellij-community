// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.yaml.syntax.lexer

import com.intellij.platform.syntax.util.lexer.FlexAdapter
import com.intellij.platform.syntax.util.lexer.FlexLexer

/**
 * Reports the states of [YamlLexerStates] instead of the lexical states.
 * [FlexAdapter] reads the state before each token, so the state holds the fields at the start of the token.
 */
internal class YamlFlexLexer(val lexer: _YamlLexer) : FlexLexer by lexer {
  override fun yystate(): Int = lexer.packState()

  override fun reset(buffer: CharSequence, start: Int, end: Int, initialState: Int) {
    when {
      YamlLexerStates.isRestartable(initialState) -> {
        lexer.reset(buffer, start, end, _YamlLexer.YYINITIAL)
        lexer.restoreState(initialState)
      }
      // Some complex lexers (e.g. black and white lexer) require "suspending" of the lexer to pass some template language.
      // In these cases we believe that the same instance of the lexer would be restored (with its internal state).
      initialState == YamlLexerStates.DIRTY_STATE -> lexer.resetKeepingColumn(buffer, start, end, _YamlLexer.YYINITIAL)
      else -> lexer.resetKeepingColumn(buffer, start, end, initialState)
    }
  }
}
