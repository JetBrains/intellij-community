// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.psi.*

fun PsiElement?.canDeleteElement(): Boolean {
    if (this is KtObjectDeclaration && isObjectLiteral()) return false

    if (this is KtParameter) {
        val parameterList = parent as? KtParameterList ?: return false
        val declaration = parameterList.parent as? KtDeclaration ?: return false
        return declaration !is KtPropertyAccessor
    }

    return this is KtClassOrObject
            || this is KtSecondaryConstructor
            || this is KtNamedFunction
            || this is PsiMethod
            || this is PsiClass
            || this is KtProperty
            || this is KtTypeParameter
            || this is KtTypeAlias
}
