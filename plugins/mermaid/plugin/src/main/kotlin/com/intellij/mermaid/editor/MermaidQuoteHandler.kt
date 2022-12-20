package com.intellij.mermaid.editor

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.mermaid.lang.lexer.MermaidTokens

class MermaidQuoteHandler :
  SimpleTokenSetQuoteHandler(MermaidTokens.DOUBLE_QUOTE)
