// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.injection

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.TokenType
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import kotlin.math.min

private const val tripleQuotes = "```"
private const val tripleTildes = "~~~"

internal class K2KDocCodeBlockLanguageInjector : MultiHostInjector {
    private val languages: Map<String, Language> by lazy {
        Language.getRegisteredLanguages().associateBy { it.id.lowercase() }
    }

    private val elementsToInject =
        listOf(KDocSection::class.java)

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        elementsToInject

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        val host = context as? KDocSection ?: return

        var languageId: String? = null
        var fencedBlock = false
        val elements = mutableListOf<PsiElement>()

        var e: PsiElement? = host.firstChild
        while (e != null) {
            val elementType = e.elementType
            when (elementType) {
                KDocTokens.CODE_SPAN_TEXT -> {
                    injectTextRanges(registrar, KotlinLanguage.INSTANCE, listOf(e.textRangeInParent), context)
                }

                KDocTokens.TEXT -> {
                    val trim = e.text.trim()
                    val quotes = trim.startsWith(tripleQuotes)
                    val tildes = !quotes && trim.startsWith(tripleTildes)
                    val fenced = quotes || tildes
                    if (fenced) {
                        if (languageId == null) {
                            val charToDrop = if (quotes) "`" else "~"
                            languageId = trim.replace(charToDrop, "")
                            fencedBlock = true
                        }
                    }

                    if (!e.isWhiteSpace() && elements.isNotEmpty()) {
                        injectElements(registrar, context, elements, languageId, fenced)

                        languageId = null
                        fencedBlock = false
                    }
                }

                KDocTokens.CODE_BLOCK_TEXT -> {
                    if (elements.isEmpty()) {
                        val prevElement = e.prevSibling
                        elements.add(prevElement)
                    }
                    elements.add(e)
                }

                KDocTokens.LEADING_ASTERISK, TokenType.WHITE_SPACE -> {
                    if (elements.isNotEmpty()) {
                        elements.add(e)
                    }
                }
            }
            e = e.nextSibling
        }

        injectElements(registrar, context, elements, languageId, fencedBlock)
    }

    private fun injectElements(
        registrar: MultiHostRegistrar,
        context: KDocSection,
        elements: MutableList<PsiElement>,
        languageId: String?,
        fencedBlock: Boolean
    ) {
        if (elements.isEmpty()) return
        if (!elements.all { it.isWhiteSpace() }) {
            val language =
                languageId.takeIf { it?.isEmpty() != true }?.lowercase().let(languages::get)
                    ?: KotlinLanguage.INSTANCE

            injectTextRanges(registrar, language, elements.toTextRanges(fencedBlock), context)
        }
        elements.clear()
    }

    private fun PsiElement.isWhiteSpace() = this is PsiWhiteSpace || text.all { it.isWhitespace() }

    private fun List<PsiElement>.indent(): Int? {
        var indent: Int? = null

        val iterator = this.iterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            val elementType = next.elementType
            if (elementType == KDocTokens.LEADING_ASTERISK) {
                val spaces = calculateSpaces(iterator)
                indent = indent?.let { min(it, spaces) } ?: spaces
            }
            if (indent == 0) return indent
        }
        return indent
    }

    private fun calculateSpaces(iterator: Iterator<PsiElement>): Int {
        while (iterator.hasNext()) {
            val next = iterator.next()

            if (next.elementType == KDocTokens.LEADING_ASTERISK) break

            val text = next.text

            if (next is PsiWhiteSpace && text.contains("\n")) return 0

            for ((index, ch) in text.withIndex()) {
                if (!ch.isWhitespace()) return index
            }

            return text.length
        }
        return 0
    }

    private fun MutableList<PsiElement>.toTextRanges(fencedBlock: Boolean): List<TextRange> =
        buildList {
            trim(fencedBlock)

            val indent = indent() ?: return@buildList

            for (element in this@toTextRanges) {
                when {
                    element is PsiWhiteSpace -> {
                        val text = element.text
                        val indexOfNewLine = text.indexOf('\n')
                        // grab only new line `\n` text range, skip spaces
                        if (indexOfNewLine >= 0) {
                            val textRangeInParent = element.textRangeInParent
                            val startOffset = textRangeInParent.startOffset + indexOfNewLine
                            add(TextRange(startOffset, startOffset + 1))
                        }
                    }
                    element.elementType == KDocTokens.LEADING_ASTERISK -> {
                        continue
                    }
                    else -> {
                        val range = element.textRangeInParent
                        val startOffset = range.startOffset + indent
                        val endOffset = range.endOffset
                        add(TextRange(min(startOffset, endOffset), endOffset))
                    }
                }
            }
        }

    private fun MutableList<PsiElement>.trim(fencedBlock: Boolean) {
        // head whitespaces
        val iterator = this.iterator()
        while (iterator.hasNext()) {
            val element = iterator.next()
            if (element !is PsiWhiteSpace) break
            iterator.remove()
        }

        while (this.isNotEmpty()) {
            val lastOrNull = lastOrNull()
            if ((lastOrNull is PsiWhiteSpace && !fencedBlock) || lastOrNull?.elementType == KDocTokens.LEADING_ASTERISK) {
                removeLast()
            } else {
                break
            }
        }
    }

    private fun injectTextRanges(
        registrar: MultiHostRegistrar,
        language: Language,
        textRanges: List<TextRange>,
        context: KDocSection
    ) {
        if (textRanges.isEmpty()) return

        registrar
            .startInjecting(language)
            .makeInspectionsLenient(true)

        textRanges.forEach {
            registrar.addPlace(null, null, context, it)
        }

        registrar.doneInjecting()
    }
}
