// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.injection

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinLanguage

internal class K2KDocCodeBlockLanguageInjector : MultiHostInjector {
    private val languages: Map<String, Language> by lazy {
        Language.getRegisteredLanguages().associateBy { it.id.lowercase() }
    }

    private val elements =
        listOf(KDocTripleQuotesInjectionHost::class.java, KDocSpanTextInjectionHost::class.java)

    override fun elementsToInjectIn(): List<Class<out PsiElement>> = elements

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (context is KDocSpanTextInjectionHost) {
            registrar.startInjecting(KotlinLanguage.INSTANCE)
                .addPlace(null, null, context, TextRange(0, context.textLength))
                .makeInspectionsLenient(true)
                .doneInjecting()
            return
        }

        val host = context as? KDocTripleQuotesInjectionHost ?: return

        val elements = buildList {
            var e: PsiElement? = context
            while (e != null) {
                e = e.nextSibling
                when (e) {
                    is KDocBlockTextInjectionHost -> add(e)
                    is KDocTripleQuotesInjectionHost -> break
                }
            }
        }

        if (elements.isNotEmpty()) {
            registrar
                .startInjecting(languages[host.languageId] ?: KotlinLanguage.INSTANCE)
                .makeInspectionsLenient(true)
                .addPlace(null, null, context, TextRange(0, 0))

            elements.forEach {
                registrar.addPlace(null, null, it, TextRange(0, it.textLength))
            }

            registrar.doneInjecting()
        }

    }

}
