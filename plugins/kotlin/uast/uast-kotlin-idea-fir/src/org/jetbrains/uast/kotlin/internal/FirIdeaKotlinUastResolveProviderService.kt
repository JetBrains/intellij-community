// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.internal

import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.project.structure.KtNotUnderContentRootModule
import org.jetbrains.kotlin.analysis.project.structure.getKtModule
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.kotlin.FirKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.unwrapFakeFileForLightClass

class FirIdeaKotlinUastResolveProviderService : FirKotlinUastResolveProviderService {
    override fun isSupportedElement(psiElement: PsiElement): Boolean {
        if (!psiElement.isJvmElement) {
            return false
        }

        val containingFile = psiElement.containingFile?.let(::unwrapFakeFileForLightClass) as? KtFile ?: return false

        // `getKtModule` can be slow (KTIJ-25470). Since most files will be in a module or library, we can optimize this hot path using
        // `ProjectFileIndex`.
        val project = containingFile.project
        val virtualFile = containingFile.virtualFile
        if (virtualFile != null) {
            val fileIndex = ProjectRootManager.getInstance(project).fileIndex
            if (fileIndex.isInSourceContent(virtualFile) || fileIndex.isInLibrary(virtualFile)) {
                return true
            }
        }

        // The checks above might not work in all possible situations (e.g. scripts) and `getKtModule` is able to give a definitive answer.
        return containingFile.getKtModule(project) !is KtNotUnderContentRootModule
    }
}
