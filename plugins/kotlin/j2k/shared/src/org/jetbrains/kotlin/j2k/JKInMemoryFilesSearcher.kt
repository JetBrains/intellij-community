// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.psi.KtElement

abstract class JKInMemoryFilesSearcher {
    abstract fun search(element: KtElement, scope: PsiElement? = null): Iterable<PsiReference>

    companion object {
        fun create(files: List<PsiElement>) = if (files.size == 1) {
            JKSingleFileInMemoryFilesSearcher(files.single())
        } else {
            JKMultipleFilesInMemoryFilesSearcher(files)
        }
    }
}

internal class JKSingleFileInMemoryFilesSearcher(private val scopeElement: PsiElement) : JKInMemoryFilesSearcher() {
    override fun search(element: KtElement, scope: PsiElement?): Iterable<PsiReference> =
        doSearch(element, LocalSearchScope(scope ?: scopeElement))
}


// it does not cover all cases e.g, just-changed reference
// maybe the solution is to do searching manually
// firstly by-text and then resolving
internal class JKMultipleFilesInMemoryFilesSearcher(private val scopeElements: List<PsiElement>) : JKInMemoryFilesSearcher() {
    override fun search(element: KtElement, scope: PsiElement?): Iterable<PsiReference> {
        if (scope != null) {
            return doSearch(element, LocalSearchScope(scope))
        }
        val result = mutableListOf<PsiReference>()
        for (scopeElement in scopeElements) {
            result += doSearch(element, LocalSearchScope(scopeElement))
        }
        return result
    }
}

private fun doSearch(element: KtElement, scope: LocalSearchScope) =
    ReferencesSearch.search(element, scope, /* ignoreAccessScope = */ true)
