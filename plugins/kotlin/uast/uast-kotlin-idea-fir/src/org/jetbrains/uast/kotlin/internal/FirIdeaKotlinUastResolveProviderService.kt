// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin.internal

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.util.isOutsiderFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.kotlin.FirKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.internal.util.ReadActionSingleValueCache
import org.jetbrains.uast.kotlin.unwrapFakeFileForLightClass

class FirIdeaKotlinUastResolveProviderService : FirKotlinUastResolveProviderService {
    /**
     * A [ReadActionSingleValueCache] is appropriate for UAST conversions because elements from the same file are checked together. The
     * read-action cache allows us to avoid cache invalidation after module or target platform changes, and also to use the [KtFile] as a
     * key without worrying about smart pointers.
     */
    private val supportedFileCache = ReadActionSingleValueCache(::isSupportedFile)

    override fun isSupportedElement(psiElement: PsiElement): Boolean {
        val file = psiElement.containingFile?.let(::unwrapFakeFileForLightClass) as? KtFile ?: return false
        return supportedFileCache.getCachedOrEvaluate(file)
    }

    private fun isSupportedFile(file: KtFile): Boolean {
        if (!file.isJvmElement) {
            return false
        }

        val virtualFile = file.virtualFile
        return virtualFile == null || !isOutsiderFile(virtualFile)
    }
}
