// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.internal

import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.project.structure.KtNotUnderContentRootModule
import org.jetbrains.kotlin.analysis.project.structure.getKtModule
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

        // Since most files will be in a module or library, we can optimize this hot path using `ProjectFileIndex`, instead of using the
        // more expensive `getKtModule`.
        val project = file.project
        val virtualFile = file.virtualFile
        if (virtualFile != null) {
            val fileIndex = ProjectRootManager.getInstance(project).fileIndex
            if (fileIndex.isInSourceContent(virtualFile) || fileIndex.isInLibrary(virtualFile)) {
                return true
            }
        }

        // The checks above might not work in all possible situations (e.g. scripts) and `getKtModule` is able to give a definitive answer.
        return file.getKtModule(project) !is KtNotUnderContentRootModule
    }
}
