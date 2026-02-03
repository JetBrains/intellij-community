// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.psi.PsiElement
import com.intellij.refactoring.safeDelete.ImportSearcher
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class KotlinImportSearcher : ImportSearcher() {
    override fun findImport(element: PsiElement, onlyNonStatic: Boolean): PsiElement? {
        if (element.containingFile !is KtFile) {
            return null
        }
        return element.getNonStrictParentOfType<KtImportDirective>()
    }
}