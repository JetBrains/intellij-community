// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.uast.kotlin.generate.KotlinUastBaseCodeGenerationPlugin

abstract class FirKotlinUastCodeGenerationPlugin : KotlinUastBaseCodeGenerationPlugin() {
    override fun shortenReference(sourcePsi: KtElement): PsiElement? {
        shortenReferences(sourcePsi)
        return sourcePsi
    }
}