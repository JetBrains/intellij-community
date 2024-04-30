// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.copy

import com.intellij.psi.PsiFile
import com.intellij.psi.impl.file.UpdateAddedFileProcessor
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefix
import org.jetbrains.kotlin.idea.core.packageMatchesDirectoryOrImplicit
import org.jetbrains.kotlin.psi.KtFile

class KotlinUpdateAddedFileProcessor : UpdateAddedFileProcessor() {
    override fun canProcessElement(element: PsiFile): Boolean = element is KtFile

    override fun update(element: PsiFile, originalElement: PsiFile?) {
        val targetFile = element as? KtFile ?: return
        if ((originalElement == null || originalElement is KtFile && originalElement.packageMatchesDirectoryOrImplicit()) &&
            targetFile.packageMatchesDirectoryOrImplicit() != true) {
            targetFile.containingDirectory?.getFqNameWithImplicitPrefix()?.quoteIfNeeded()?.let { targetDirectoryFqName ->
                targetFile.packageFqName = targetDirectoryFqName
            }
        }
    }
}