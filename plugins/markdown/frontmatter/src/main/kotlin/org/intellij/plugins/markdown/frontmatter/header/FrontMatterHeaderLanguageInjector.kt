package org.intellij.plugins.markdown.frontmatter.header

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.parser.blocks.frontmatter.FrontMatterHeaderMarkerProvider
import org.intellij.plugins.markdown.lang.parser.blocks.frontmatter.FrontMatterLanguages
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFrontMatterHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFrontMatterHeaderContent
import org.jetbrains.yaml.YAMLLanguage
import org.toml.lang.TomlLanguage

internal class FrontMatterHeaderLanguageInjector: MultiHostInjector {
  override fun getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement) {
    if (!FrontMatterHeaderMarkerProvider.isFrontMatterSupportEnabled()) {
      return
    }
    if (host !is MarkdownFrontMatterHeader || !host.isValidHost) {
      return
    }
    if (host.children.all { it.elementType != MarkdownElementTypes.FRONT_MATTER_HEADER_CONTENT }) {
      return
    }
    val contentElement = host.children.filterIsInstance<MarkdownFrontMatterHeaderContent>().firstOrNull() ?: return
    val language = when (host.contentLanguage) {
      FrontMatterLanguages.YAML -> YAMLLanguage.INSTANCE
      FrontMatterLanguages.TOML -> TomlLanguage
      else -> return
    }
    registrar.apply {
      startInjecting(language)
      addPlace(null, null, host, contentElement.textRangeInParent)
      doneInjecting()
    }
  }

  override fun elementsToInjectIn(): MutableList<out Class<out PsiElement>> {
    return mutableListOf(MarkdownFrontMatterHeader::class.java)
  }
}
