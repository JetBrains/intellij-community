// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fenceInjection

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.util.text.TextRangeUtil
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
    val (language, extension) = findLangForInjection(host) ?: return
    if (!canBeInjected(language)) {
      return
    }
    registrar.startInjecting(language, extension)
    injectInMultiplePlaces(host, registrar, language)
    registrar.makeInspectionsLenient(true)
    registrar.doneInjecting()
  }

  private fun canBeInjected(language: Language): Boolean {
    return LanguageParserDefinitions.INSTANCE.forLanguage(language) != null
  }

  protected open fun findLangForInjection(element: MarkdownCodeFence): Pair<Language, String?>? {
    val name = element.fenceLanguage ?: return null
    return CodeFenceLanguageGuesser.guessLanguageWithExtensionForInjection(name).takeIf {
      MarkdownSettings.getInstance(element.project).areInjectionsEnabled
    }
  }

  /**
   * Such a code fence will make use of IntelliJ Formatter.
   *
   * But the problem is that not all formatters are ready to work in the injected context, so we should do it with great care.
   */
  private fun injectInMultiplePlaces(host: MarkdownCodeFence, registrar: MultiHostRegistrar, language: Language) {
    val elements = MarkdownCodeFence.obtainFenceContent(host, withWhitespaces = false) ?: return
    val ranges = TextRangeUtil.mergeRanges(elements.map { it.textRangeInParent }).sortedBy { it.startOffset }

    val surroundings = FenceSurroundingsProvider.EP_NAME.extensionList.find { it.language == language }?.getCodeFenceSurroundings()
    if (ranges.size == 1) {
      registrar.addPlace(surroundings?.prefix, surroundings?.suffix, host, ranges.first())
      return
    }

    registrar.addPlace(surroundings?.prefix, null, host, ranges.first())
    for (range in ranges.drop(1).dropLast(1)) {
      registrar.addPlace(null, null, host, range)
    }
    registrar.addPlace(null, surroundings?.suffix, host, ranges.last())
  }
}
