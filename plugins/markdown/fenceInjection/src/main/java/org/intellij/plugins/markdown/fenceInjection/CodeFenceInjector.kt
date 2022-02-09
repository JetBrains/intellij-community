// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fenceInjection

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.settings.MarkdownSettings

/**
 * Injector for Markdown code-fences
 *
 * It is capable of injecting code in:
 *
 * * Top-level code fences with support of formatting and
 *   correct alignment on enter
 *
 * * Blockquoted/in-list code fences with formatting model.
 *   But in that case indentation is disabled via [MarkdownEnterHandler]
 *   and formatting is disable via [MarkdownFormattingBlock].
 *   The reason for it is numerous problems with injection-based
 *   formatting.
 */
internal open class CodeFenceInjector : MultiHostInjector {
  companion object {
    private val toInject = listOf(MarkdownCodeFence::class.java)
  }

  override fun elementsToInjectIn(): List<Class<out PsiElement>?> = toInject

  override fun getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement) {
    if (host !is MarkdownCodeFence || !host.isValidHost || host.children.all { it.elementType != MarkdownTokenTypes.CODE_FENCE_CONTENT }) {
      return
    }

    val language = findLangForInjection(host) ?: return

    registrar.startInjecting(language)
    injectAsOnePlace(host, registrar)
    registrar.doneInjecting()
  }


  protected open fun findLangForInjection(element: MarkdownCodeFence): Language? {
    val name = element.fenceLanguage ?: return null
    return CodeFenceLanguageGuesser.guessLanguageForInjection(name).takeIf {
      MarkdownSettings.getInstance(element.project).areInjectionsEnabled
    }
  }

  /**
   * Such code fence will make use of IntelliJ Formatter.
   *
   * But, the problem is that not all formatters are ready to work in
   * injected context, so we should do it with great care.
   */
  private fun injectAsOnePlace(host: MarkdownCodeFence, registrar: MultiHostRegistrar) {
    val elements = MarkdownCodeFenceUtils.getContent(host, withWhitespaces = true) ?: return

    val first = elements.first()
    val last = elements.last()

    registrar.addPlace(null, null, host, TextRange.create(first.startOffsetInParent, last.startOffsetInParent + last.textLength))
  }
}
