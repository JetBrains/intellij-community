// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.psi.PsiElement
import com.intellij.psi.search.ScopeOptimizer
import com.intellij.psi.search.SearchScope

class KotlinScopeOptimizer : ScopeOptimizer {
    override fun getRestrictedUseScope(element: PsiElement): SearchScope? =
        KotlinCompilerReferenceIndexService.getInstanceIfEnable(element.project)?.scopeWithCodeReferences(element)
}