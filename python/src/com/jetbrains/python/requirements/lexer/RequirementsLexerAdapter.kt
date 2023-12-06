package com.jetbrains.python.requirements.lexer

import com.intellij.lexer.FlexAdapter

class RequirementsLexerAdapter: FlexAdapter(
  RequirementsLexer(null)) {
}