// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search

import com.intellij.model.search.SearchService
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.util.Query
import org.jetbrains.annotations.NotNull
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.psi.KtClass

class DirectKotlinClassInheritorsSearch {

    data class SearchParameters(val ktClass: KtClass, val searchScope: SearchScope, val includeAnonymous : Boolean = true) 
        : com.intellij.model.search.SearchParameters<KtClassOrObjectSymbol> {
        @NotNull
        override fun getProject(): Project {
            return ktClass.project
        }

        override fun areValid(): Boolean {
            return ktClass.isValid
        }
    }
    
    companion object {

        fun search(klass: KtClass): Query<KtClassOrObjectSymbol> {
            return search(SearchParameters(klass, klass.useScope))
        }

        fun search(klass: KtClass, searchScope: SearchScope): Query<KtClassOrObjectSymbol> {
            return search(SearchParameters(klass, searchScope))
        }

        fun search(parameters: SearchParameters): Query<KtClassOrObjectSymbol> {
            return SearchService.getInstance().searchParameters(parameters)
        }
    }
}