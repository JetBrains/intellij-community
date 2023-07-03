package com.intellij.mermaid.lang.injection

import com.intellij.lang.injection.general.Injection
import com.intellij.lang.injection.general.LanguageInjectionContributor
import com.intellij.lang.injection.general.SimpleInjection
import com.intellij.mermaid.lang.psi.MermaidMarkdownValue
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.lang.MarkdownLanguage

class MarkdownInjector : LanguageInjectionContributor {
  override fun getInjection(context: PsiElement): Injection? {
    if (context is MermaidMarkdownValue) {
      return SimpleInjection(
        MarkdownLanguage.INSTANCE, "", "", null
      )
    }
    return null
  }
}
