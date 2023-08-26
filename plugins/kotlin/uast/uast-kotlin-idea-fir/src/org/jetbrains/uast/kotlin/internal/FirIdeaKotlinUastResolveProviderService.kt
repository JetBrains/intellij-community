// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin.internal

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.util.isOutsiderFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.kotlin.FirKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.unwrapFakeFileForLightClass

class FirIdeaKotlinUastResolveProviderService : FirKotlinUastResolveProviderService {
    override fun isSupportedElement(psiElement: PsiElement): Boolean {
        val file = psiElement.containingFile?.let(::unwrapFakeFileForLightClass) as? KtFile ?: return false
        val virtualFile = file.virtualFile
        return virtualFile == null || !isOutsiderFile(virtualFile)
    }
}
