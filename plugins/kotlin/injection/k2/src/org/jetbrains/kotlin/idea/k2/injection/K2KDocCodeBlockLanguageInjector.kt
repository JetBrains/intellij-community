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
                    if (quotes || tildes) {
                        if (languageId == null) {
                            val charToDrop = if (quotes) "`" else "~"
                            languageId = trim.replace(charToDrop, "")
                        }
                    }

                    if (elements.isNotEmpty()) {
                        injectElements(registrar, context, elements, languageId ?: "")

                        languageId = null
                    }
                }

                KDocTokens.CODE_BLOCK_TEXT -> {
                    elements.add(e)
                }

                TokenType.WHITE_SPACE -> {
                    if (elements.isNotEmpty()) {
                        elements.add(e)
                    }
                }
            }
            e = e.nextSibling
        }

        if (languageId != null) {
            injectElements(registrar, context, elements, languageId)
        }
    }

    private fun injectElements(
        registrar: MultiHostRegistrar,
        context: KDocSection,
        elements: MutableList<PsiElement>,
        languageId: String
    ) {
        if (elements.isEmpty()) return

        val language =
            languageId.takeIf { it.isNotEmpty() }?.lowercase().let(languages::get)
                ?: KotlinLanguage.INSTANCE

        injectTextRanges(registrar, language, elements.toTextRanges(), context)
        elements.clear()
    }

    private fun MutableList<PsiElement>.indent(): Int? {
        var indent: Int? = null

        for (element in this) {
            val text = element.text
            if (element is PsiWhiteSpace) continue

            for ((index, ch) in text.withIndex()) {
                if (!Character.isWhitespace(ch)) {
                    indent = if (indent == null) index else min(indent, index)
                    break
                }
            }
            if (indent == 0) break
        }
        return indent
    }

    private fun MutableList<PsiElement>.toTextRanges(): List<TextRange> =
        buildList {

            trim()

            val indent = indent() ?: return@buildList

            for (element in this@toTextRanges) {
                if (element is PsiWhiteSpace) {
                    val text = element.text
                    val indexOfNewLine = text.indexOf('\n')
                    // grab only new line `\n` text range, skip spaces
                    if (indexOfNewLine >= 0) {
                        val textRangeInParent = element.textRangeInParent
                        val startOffset = textRangeInParent.startOffset + indexOfNewLine
                        add(TextRange(startOffset, startOffset + 1))
                    }
                } else {
                    val range = element.textRangeInParent
                    val startOffset = range.startOffset + indent
                    val endOffset = range.endOffset
                    add(TextRange(min(startOffset, endOffset), endOffset))
                }
            }
        }

    private fun MutableList<PsiElement>.trim() {
        // head whitespaces
        val iterator = this.iterator()
        while (iterator.hasNext()) {
            val element = iterator.next()
            if (element !is PsiWhiteSpace) break
            iterator.remove()
        }

        // tail whitespaces
        val listIterator = this.listIterator(lastIndex + 1)

        do {
            val element = listIterator.previous()
            if (element !is PsiWhiteSpace) break

            listIterator.remove()
        } while ((listIterator.hasPrevious()))
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
