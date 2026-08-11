// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fenceInjection

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
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
    val places = createInjectionPlaces(elements)

    val surroundings = FenceSurroundingsProvider.EP_NAME.extensionList.find { it.language == language }?.getCodeFenceSurroundings()
    if (places.size == 1) {
      val place = places.single()
      registrar.addPlace(
        combine(surroundings?.prefix, place.prefix),
        combine(place.suffix, surroundings?.suffix),
        host, place.range
      )
      return
    }

    val first = places.first()
    val last = places.last()

    registrar.addPlace(combine(surroundings?.prefix, first.prefix), first.suffix, host, first.range)
    for ((range, prefix, suffix) in places.drop(1).dropLast(1)) {
      registrar.addPlace(prefix, suffix, host, range)
    }
    registrar.addPlace(last.prefix, combine(last.suffix, surroundings?.suffix), host, last.range)
  }

  private fun createInjectionPlaces(elements: List<PsiElement>): List<InjectionPlace> {
    val places = mutableListOf<InjectionPlace>()
    var prefix = ""
    for (group in groupAdjacentElements(elements)) {
      if (group.all { it is PsiWhiteSpace }) {
        val whitespace = group.joinToString(separator = "") { it.text }
        if (places.isEmpty()) {
          prefix += whitespace
        } else {
          val last = places.last()
          places[places.lastIndex] = last.copy(suffix = last.suffix + whitespace)
        }
      } else {
        val range = TextRange(group.first().startOffsetInParent, group.last().startOffsetInParent + group.last().textLength)
        places += InjectionPlace(range, prefix)
        prefix = ""
      }
    }
    return places
  }

  private fun groupAdjacentElements(elements: List<PsiElement>): List<List<PsiElement>> {
    val groups = mutableListOf<MutableList<PsiElement>>()
    for (element in elements) {
      val previous = groups.lastOrNull()?.lastOrNull()
      if (previous == null || previous.textRangeInParent.endOffset < element.textRangeInParent.startOffset) {
        groups += mutableListOf(element)
      } else {
        groups.last() += element
      }
    }
    return groups
  }

  private data class InjectionPlace(val range: TextRange, val prefix: String = "", val suffix: String = "")

  private fun combine(first: String?, second: String?): String? = (first.orEmpty() + second.orEmpty()).ifEmpty { null }
}
