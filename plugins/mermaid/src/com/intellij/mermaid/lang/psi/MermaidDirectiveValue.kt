// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.tree.IElementType

class MermaidDirectiveValue(type: IElementType, text: CharSequence): MermaidLeafPsiElement(type, text), PsiLanguageInjectionHost {
  override fun isValidHost(): Boolean {
    return true
  }

  override fun updateText(text: String): PsiLanguageInjectionHost {
    return ElementManipulators.handleContentChange(this, text)
  }

  override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> {
    return DirectiveTextEscaper(this)
  }

  private class DirectiveTextEscaper(host: MermaidDirectiveValue): LiteralTextEscaper<PsiLanguageInjectionHost>(host) {
    override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
      outChars.append(rangeInsideHost.substring(myHost.text))
      return true
    }

    override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int {
      return rangeInsideHost.startOffset + offsetInDecoded
    }

    override fun isOneLine(): Boolean {
      return false
    }
  }
}
