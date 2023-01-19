// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement

class KotlinFirRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isSafeDeleteAvailable(element: PsiElement) = element.canDeleteElement()

    /**
     * @see org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringSupportProvider.isInplaceRenameAvailable
     */
    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean = false

    /**
     * @see org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringSupportProvider.isMemberInplaceRenameAvailable
     */
    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean = false
}