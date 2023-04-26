// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope

interface KotlinMoveRefactoringSupport {
    companion object {
        @JvmStatic
        fun getInstance(): KotlinMoveRefactoringSupport = service()
    }

    fun findReferencesToHighlight(target: PsiElement, searchScope: SearchScope): Collection<PsiReference>
}