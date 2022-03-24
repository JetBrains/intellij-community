// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.psi.PsiElement
import com.intellij.psi.search.ScopeOptimizer
import com.intellij.psi.search.SearchScope

class KotlinScopeOptimizer : ScopeOptimizer {
    override fun getRestrictedUseScope(element: PsiElement): SearchScope? =
        KotlinCompilerReferenceIndexService.getInstanceIfEnabled(element.project)?.scopeWithCodeReferences(element)
}