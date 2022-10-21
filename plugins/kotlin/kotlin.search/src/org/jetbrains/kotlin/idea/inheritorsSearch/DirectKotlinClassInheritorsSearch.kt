// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inheritorsSearch

import com.intellij.model.search.SearchService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.util.Query
import org.jetbrains.kotlin.psi.KtClass

object DirectKotlinClassInheritorsSearch {

    data class SearchParameters(
        val ktClass: KtClass,
        val searchScope: SearchScope,
        val includeAnonymous: Boolean = true
    ) : com.intellij.model.search.SearchParameters<PsiElement> {
        override fun getProject(): Project {
            return ktClass.project
        }

        override fun areValid(): Boolean {
            return ktClass.isValid
        }
    }

    fun search(klass: KtClass): Query<PsiElement> {
        return search(SearchParameters(klass, klass.useScope))
    }

    fun search(klass: KtClass, searchScope: SearchScope): Query<PsiElement> {
        return search(SearchParameters(klass, searchScope))
    }

    fun search(parameters: SearchParameters): Query<PsiElement> {
        return SearchService.getInstance().searchParameters(parameters)
    }
  
}