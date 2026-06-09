// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang

import com.intellij.mermaid.lang.lexer.MermaidTokens.ALIAS
import com.intellij.mermaid.lang.lexer.MermaidTokens.ClassDiagram.CLASS_ID
import com.intellij.mermaid.lang.lexer.MermaidTokens.Flowchart.LINK_TEXT
import com.intellij.mermaid.lang.lexer.MermaidTokens.ID
import com.intellij.mermaid.lang.lexer.MermaidTokens.LABEL
import com.intellij.mermaid.lang.lexer.MermaidTokens.LINE_COMMENT
import com.intellij.mermaid.lang.lexer.MermaidTokens.NOTE_CONTENT
import com.intellij.mermaid.lang.lexer.MermaidTokens.SECTION_TITLE
import com.intellij.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
import com.intellij.mermaid.lang.lexer.MermaidTokens.Sequence.MESSAGE
import com.intellij.mermaid.lang.lexer.MermaidTokens.TASK_NAME
import com.intellij.mermaid.lang.lexer.MermaidTokens.TITLE_VALUE
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.elementType
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.Tokenizer

class MermaidSpellcheckingStrategy : SpellcheckingStrategy() {
  private val tokensWithText = TokenSet.create(
    LINE_COMMENT,
    TITLE_VALUE,
    ID,
    CLASS_ID,
    ALIAS,
    NOTE_CONTENT,
    SECTION_TITLE,
    TASK_NAME,
    LINK_TEXT,
    MESSAGE,
    LABEL,
    STRING_VALUE
  )

  override fun getTokenizer(element: PsiElement?): Tokenizer<*> {
    return when (element.elementType) {
      in tokensWithText -> TEXT_TOKENIZER
      else -> EMPTY_TOKENIZER
    }
  }
}
