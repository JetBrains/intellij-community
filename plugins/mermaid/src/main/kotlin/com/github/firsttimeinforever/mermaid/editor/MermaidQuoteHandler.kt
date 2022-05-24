package com.github.firsttimeinforever.mermaid.editor

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler

class MermaidQuoteHandler :
  SimpleTokenSetQuoteHandler(MermaidTokens.DOUBLE_QUOTE, MermaidTokens.Flowchart.SEP)
