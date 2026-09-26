package org.intellij.plugins.markdown.frontmatter.yaml

import com.intellij.lang.injection.general.Injection
import com.intellij.lang.injection.general.LanguageInjectionContributor
import com.intellij.lang.injection.general.SimpleInjection
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.lang.parser.blocks.frontmatter.FrontMatterLanguages
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFrontMatterHeader
import org.jetbrains.yaml.YAMLLanguage

internal class FrontMatterHeaderYamlInjectionContributor: LanguageInjectionContributor {
  override fun getInjection(context: PsiElement): Injection? {
    val host = context as? MarkdownFrontMatterHeader ?: return null
    if (!host.isValidHost) {
      return null
    }
    return when (host.contentLanguage) {
      FrontMatterLanguages.YAML -> SimpleInjection(YAMLLanguage.INSTANCE, "", "", null)
      else -> null
    }
  }
}
