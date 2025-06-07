// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.searching.inheritors

import com.intellij.model.search.SearchService
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.util.Query
import org.jetbrains.kotlin.psi.KtClass

/**
 * A search for the direct inheritors of a [KtClass].
 *
 * The search is performed by the following searchers:
 *
 *  - [DirectKotlinClassInheritorsSearcher] searches for Kotlin inheritors.
 *  - [DirectKotlinClassDelegatedSearcher] searches for non-Kotlin inheritors.
 */
object DirectKotlinClassInheritorsSearch {
    data class SearchParameters(
        val ktClass: KtClass,
        val searchScope: SearchScope,
        val includeAnonymous: Boolean = true,
        val includeLocal: Boolean = true,
    ) : com.intellij.model.search.SearchParameters<PsiElement> {
        override fun getProject(): Project {
            return runReadAction { ktClass.project }
        }

        override fun areValid(): Boolean {
            return ktClass.isValid
        }
    }

    fun search(klass: KtClass): Query<out PsiElement> {
        return search(SearchParameters(klass, runReadAction { klass.useScope }))
    }

    fun search(klass: KtClass, searchScope: SearchScope): Query<out PsiElement> {
        return search(SearchParameters(klass, searchScope))
    }

    fun search(parameters: SearchParameters): Query<out PsiElement> {
        return SearchService.getInstance().searchParameters(parameters)
    }
}
