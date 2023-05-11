// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.internal

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.project.structure.KtNotUnderContentRootModule
import org.jetbrains.kotlin.analysis.project.structure.getKtModule
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.kotlin.FirKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.unwrapFakeFileForLightClass

class FirCliKotlinUastResolveProviderService : FirKotlinUastResolveProviderService {
    override fun isSupportedElement(psiElement: PsiElement): Boolean {
        // Currently, UAST CLI is used by Android Lint, i.e., everything is a JVM element, so we don't have to check the target platform.
        // The `getKtModule` optimization of `FirIdeaKotlinUastResolveProviderService` cannot be applied here, because `uast-kotlin-fir` is
        // available externally and doesn't have access to `intellij.platform.projectModel`. See also KTIJ-24932.
        val containingFile = psiElement.containingFile?.let(::unwrapFakeFileForLightClass) as? KtFile ?: return false
        return containingFile.getKtModule(containingFile.project) !is KtNotUnderContentRootModule
    }
}
