package com.intellij.mermaid.lang.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.tree.IElementType

class MermaidFrontmatterContent(type: IElementType, text: CharSequence): MermaidLeafPsiElement(type, text), PsiLanguageInjectionHost {
  override fun isValidHost(): Boolean {
    return true
  }

  override fun updateText(text: String): PsiLanguageInjectionHost {
    return ElementManipulators.handleContentChange(this, text)
  }

  override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> {
    return FrontmatterTextEscaper(this)
  }

  private class FrontmatterTextEscaper(host: MermaidFrontmatterContent): LiteralTextEscaper<PsiLanguageInjectionHost>(host) {
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
