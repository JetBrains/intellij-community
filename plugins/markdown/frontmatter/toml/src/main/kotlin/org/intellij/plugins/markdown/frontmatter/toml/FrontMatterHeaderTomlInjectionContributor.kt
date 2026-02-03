package org.intellij.plugins.markdown.frontmatter.toml

import com.intellij.lang.injection.general.Injection
import com.intellij.lang.injection.general.LanguageInjectionContributor
import com.intellij.lang.injection.general.SimpleInjection
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.lang.parser.blocks.frontmatter.FrontMatterLanguages
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFrontMatterHeader
import org.toml.lang.TomlLanguage

internal class FrontMatterHeaderTomlInjectionContributor: LanguageInjectionContributor {
  override fun getInjection(context: PsiElement): Injection? {
    val host = context as? MarkdownFrontMatterHeader ?: return null
    if (!host.isValidHost) {
      return null
    }
    return when (host.contentLanguage) {
      FrontMatterLanguages.TOML -> SimpleInjection(TomlLanguage, "", "", null)
      else -> null
    }
  }
}
