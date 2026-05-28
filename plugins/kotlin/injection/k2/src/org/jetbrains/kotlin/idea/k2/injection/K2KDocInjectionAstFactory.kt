// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.injection

import com.intellij.lang.ASTFactory
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.kdoc.psi.api.KDocElement

internal class K2KDocInjectionAstFactory : ASTFactory() {
    override fun createLeaf(type: IElementType, text: CharSequence): LeafElement? {
        return when (type) {
            KDocTokens.CODE_SPAN_TEXT -> KDocSpanTextInjectionHost(type, text)
            KDocTokens.CODE_BLOCK_TEXT -> KDocBlockTextInjectionHost(type, text)
            KDocTokens.TEXT -> {
                val trim = text.trim()
                val index = trim.indexOf("```")
                if (index >= 0) {
                    val languageId = trim.substring(index + 3)
                    KDocTripleQuotesInjectionHost(type, text, languageId.takeIf { it.isNotEmpty() })
                } else {
                    null
                }
            }

            else -> null
        }
    }
}

abstract class AbstractKDocCodeInjectionHost(
    type: IElementType,
    text: CharSequence
) :
    LeafPsiElement(type, text),
    KDocElement,
    PsiLanguageInjectionHost {
    override fun isValidHost(): Boolean = true

    override fun updateText(text: String): PsiLanguageInjectionHost =
        replaceWithText(text) as PsiLanguageInjectionHost

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        LiteralTextEscaper.createSimple(this, false)
}

internal class KDocSpanTextInjectionHost(type: IElementType, text: CharSequence) : AbstractKDocCodeInjectionHost(type, text)
internal class KDocBlockTextInjectionHost(type: IElementType, text: CharSequence) : AbstractKDocCodeInjectionHost(type, text)
internal class KDocTripleQuotesInjectionHost(type: IElementType, text: CharSequence, val languageId: String?) : AbstractKDocCodeInjectionHost(type, text)
