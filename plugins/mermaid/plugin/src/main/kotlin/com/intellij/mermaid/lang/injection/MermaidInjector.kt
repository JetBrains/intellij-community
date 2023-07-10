package com.intellij.mermaid.lang.injection

import com.intellij.json.json5.Json5Language
import com.intellij.lang.injection.general.Injection
import com.intellij.lang.injection.general.LanguageInjectionContributor
import com.intellij.lang.injection.general.SimpleInjection
import com.intellij.mermaid.lang.psi.MermaidDirectiveValue
import com.intellij.mermaid.lang.psi.MermaidFrontmatterContent
import com.intellij.mermaid.lang.psi.MermaidMarkdownValue
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.jetbrains.yaml.YAMLLanguage

class MermaidInjector : LanguageInjectionContributor {
  override fun getInjection(context: PsiElement): Injection? {
    if (context is MermaidMarkdownValue) {
      return SimpleInjection(MarkdownLanguage.INSTANCE, "", "", null)
    }
    if (context is MermaidDirectiveValue) {
      return SimpleInjection(Json5Language.INSTANCE, "", "", null)
    }
    if (context is MermaidFrontmatterContent) {
      return SimpleInjection(YAMLLanguage.INSTANCE, "", "", null)
    }
    return null
  }
}
