// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.memberInfo

import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory

interface KtPsiClassWrapper : KtNamedDeclaration {
    val psiClass: PsiClass
}

fun KtPsiClassWrapper(psiClass: PsiClass): KtPsiClassWrapper {
    val dummyKtClass = KtPsiFactory(psiClass.project).createClass("class ${psiClass.name}")
    return object : KtPsiClassWrapper, KtNamedDeclaration by dummyKtClass {
        override fun equals(other: Any?) = psiClass == (other as? KtPsiClassWrapper)?.psiClass

        override fun hashCode() = psiClass.hashCode()

        override val psiClass: PsiClass
            get() = psiClass
    }
}