package org.intellij.plugins.markdown.frontmatter.header

import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.lang.injection.general.Injection
import com.intellij.lang.injection.general.LanguageInjectionPerformer
import com.intellij.psi.PsiElement
import org.intellij.plugins.intelliLang.inject.InjectedLanguage
import org.intellij.plugins.intelliLang.inject.InjectorUtils
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFrontMatterHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFrontMatterHeaderContent

internal class FrontMatterHeaderLanguageInjectionPerformer: LanguageInjectionPerformer {
  override fun isPrimary(): Boolean {
    return true
  }

  override fun performInjection(registrar: MultiHostRegistrar, injection: Injection, context: PsiElement): Boolean {
    val host = context as? MarkdownFrontMatterHeader ?: return false
    if (!host.isValidHost) {
      return false
    }
    val language = InjectorUtils.getLanguageByString(injection.injectedLanguageId) ?: return false
    val injectedLanguage = injection.createInjectedLanguage() ?: return false
    val file = host.containingFile ?: return false
    val contentElement = host.children.filterIsInstance<MarkdownFrontMatterHeaderContent>().singleOrNull() ?: return false
    val info = InjectorUtils.InjectionInfo(host, injectedLanguage, contentElement.textRangeInParent)
    InjectorUtils.registerInjection(language, file, listOf(info), registrar)
    return true
  }

  private fun Injection.createInjectedLanguage(): InjectedLanguage? {
    return InjectedLanguage.create(injectedLanguageId, prefix, suffix, false)
  }
}
