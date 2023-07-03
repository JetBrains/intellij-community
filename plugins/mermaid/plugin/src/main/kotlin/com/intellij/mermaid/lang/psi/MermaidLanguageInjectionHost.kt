package com.intellij.mermaid.lang.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.ElementManipulators
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost

abstract class MermaidLanguageInjectionHost(node: ASTNode) : ASTWrapperPsiElement(node), PsiLanguageInjectionHost {
  override fun isValidHost(): Boolean {
    return true
  }

  override fun updateText(text: String): PsiLanguageInjectionHost {
    return ElementManipulators.handleContentChange(this, text)
  }

  override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> {
    return LiteralTextEscaper.createSimple(this)
  }
}
