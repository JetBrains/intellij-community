// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.internal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol

/**
 * This service is required to add an ability to provide correct [PsiElement] for libraries in UAST/Lint CLI mode
 */
interface FirKotlinUastLibraryPsiProviderService {
    fun KaSession.provide(symbol: KaSymbol): PsiElement?

    class Default : FirKotlinUastLibraryPsiProviderService {
        override fun KaSession.provide(symbol: KaSymbol): PsiElement? = symbol.psi
    }

    companion object {
        fun getInstance(): FirKotlinUastLibraryPsiProviderService = ApplicationManager.getApplication().service()
    }
}
