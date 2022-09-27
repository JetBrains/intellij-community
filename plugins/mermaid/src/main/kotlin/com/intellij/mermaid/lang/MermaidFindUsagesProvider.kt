package com.intellij.mermaid.lang

import com.intellij.mermaid.lang.lexer.MermaidLexer
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.parser.MermaidElements
import com.intellij.mermaid.lang.psi.MermaidNamedElement
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet


class MermaidFindUsagesProvider: FindUsagesProvider {
  override fun getWordsScanner(): WordsScanner {
    return DefaultWordsScanner(
      MermaidLexer(),
      TokenSet.create(MermaidElements.IDENTIFIER, MermaidElements.COMPLEX_IDENTIFIER),
      TokenSet.create(MermaidTokens.LINE_COMMENT),
      TokenSet.EMPTY
    )
  }

  override fun canFindUsagesFor(psiElement: PsiElement): Boolean {
    return psiElement is MermaidNamedElement
  }

  override fun getHelpId(psiElement: PsiElement): String? {
    return null
  }

  override fun getType(element: PsiElement): String {
    return "identifier"
  }

  override fun getDescriptiveName(element: PsiElement): String {
    return element.text
  }

  override fun getNodeText(element: PsiElement, useFullName: Boolean): String {
    return element.text
  }
}
