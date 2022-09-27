package com.intellij.mermaid.editor

import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler

class MermaidQuoteHandler :
  SimpleTokenSetQuoteHandler(MermaidTokens.DOUBLE_QUOTE)
