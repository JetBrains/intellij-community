// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fenceInjection

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.settings.MarkdownSettings

/**
 * Injector for Markdown code-fences
 *
 * It is capable of injecting code in:
 *
 * * Top-level code fences with support of formatting and correct alignment on Enter.
 *
 * * Blockquote/in-list code fences with a formatting model.
 *   But in that case indentation is disabled via [MarkdownEnterHandler]
 *   and formatting is disabled via [MarkdownFormattingBlock].
 *   The reason for it is many problems with injection-based formatting.
 */
internal open class CodeFenceInjector : MultiHostInjector {
  private val toInject = listOf(MarkdownCodeFence::class.java)

  override fun elementsToInjectIn(): List<Class<out PsiElement>?> = toInject

  override fun getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement) {
    if (host !is MarkdownCodeFence || !host.isValidHost) {
      return
    }
    if (host.children.all { it.elementType != MarkdownTokenTypes.CODE_FENCE_CONTENT }) {
      return
    }
    val language = findLangForInjection(host) ?: return
    if (!canBeInjected(language)) {
      return
    }
    registrar.startInjecting(language)
    injectAsOnePlace(host, registrar, language)
    registrar.makeInspectionsLenient(true)
    registrar.doneInjecting()
  }

  private fun canBeInjected(language: Language): Boolean {
    return LanguageParserDefinitions.INSTANCE.forLanguage(language) != null
  }

  protected open fun findLangForInjection(element: MarkdownCodeFence): Language? {
    val name = element.fenceLanguage ?: return null
    return CodeFenceLanguageGuesser.guessLanguageForInjection(name).takeIf {
      MarkdownSettings.getInstance(element.project).areInjectionsEnabled
    }
  }

  /**
   * Such a code fence will make use of IntelliJ Formatter.
   *
   * But the problem is that not all formatters are ready to work in the injected context, so we should do it with great care.
   */
  private fun injectAsOnePlace(host: MarkdownCodeFence, registrar: MultiHostRegistrar, language: Language) {
    val elements = MarkdownCodeFence.obtainFenceContent(host, withWhitespaces = true) ?: return

    val first = elements.first()
    val last = elements.last()

    val surroundings = FenceSurroundingsProvider.EP_NAME.extensionList.find { it.language == language }?.getCodeFenceSurroundings()

    val range = TextRange.create(first.startOffsetInParent, last.startOffsetInParent + last.textLength)
    registrar.addPlace(surroundings?.prefix, surroundings?.suffix, host, range)
  }
}
