// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.uast.kotlin.generate.KotlinUastBaseCodeGenerationPlugin
import org.jetbrains.uast.kotlin.internal.analyzeForUast

abstract class FirKotlinUastCodeGenerationPlugin : KotlinUastBaseCodeGenerationPlugin() {
  override fun shortenReference(sourcePsi: KtElement): PsiElement {
    val ktFile = sourcePsi.containingKtFile
    analyzeForUast(ktFile) {
        collectPossibleReferenceShortenings(ktFile, sourcePsi.textRange)
    }

    //todo apply shortening
    return sourcePsi
  }
}