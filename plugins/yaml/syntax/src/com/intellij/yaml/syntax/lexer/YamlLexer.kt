package com.intellij.yaml.syntax.lexer

import com.intellij.platform.syntax.lexer.RestartableLexer
import com.intellij.platform.syntax.lexer.TokenIterator
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.platform.syntax.util.lexer.MergingLexerAdapter
import com.intellij.yaml.syntax.YamlSyntaxTokenTypes

/**
 * The YAML lexer.
 * It can restart at each line start, see [YamlLexerStates].
 */
class YamlLexer : MergingLexerAdapter(YamlFlexAdapter(YamlFlexLexer(_YamlLexer())), TOKENS_TO_MERGE), RestartableLexer {
  override fun getStartState(): Int = 0

  override fun isRestartableState(state: Int): Boolean = YamlLexerStates.isRestartable(state)

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int, tokenIterator: TokenIterator) {
    start(buffer, startOffset, endOffset, initialState)
  }
}

private val TOKENS_TO_MERGE = syntaxElementTypeSetOf(YamlSyntaxTokenTypes.TEXT)
