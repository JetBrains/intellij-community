package com.intellij.yaml.syntax.lexer

import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.platform.syntax.util.lexer.MergingLexerAdapter
import com.intellij.yaml.syntax.YamlSyntaxTokenTypes

class YamlLexer : MergingLexerAdapter(YamlFlexAdapter(_YamlLexer()), TOKENS_TO_MERGE)

private val TOKENS_TO_MERGE = syntaxElementTypeSetOf(YamlSyntaxTokenTypes.TEXT)
