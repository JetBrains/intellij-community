// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ExtensibleQueryFactory
import com.intellij.util.Query
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.psi.KtClass

class DirectKotlinClassInheritorsSearch private constructor() :
    ExtensibleQueryFactory<KtClassOrObjectSymbol, DirectKotlinClassInheritorsSearch.SearchParameters>(ExtensionPointName.create("com.intellij.directKotlinClassInheritorsSearch")) {

    data class SearchParameters(val ktClass: KtClass, val searchScope: SearchScope, val includeAnonymous : Boolean = true)
    
    companion object {
        private val INSTANCE = DirectKotlinClassInheritorsSearch() 

        fun search(klass: KtClass): Query<KtClassOrObjectSymbol> {
            return search(SearchParameters(klass, klass.useScope))
        }

        fun search(klass: KtClass, searchScope: SearchScope): Query<KtClassOrObjectSymbol> {
            return search(SearchParameters(klass, searchScope))
        }

        fun search(parameters: SearchParameters): Query<KtClassOrObjectSymbol> {
            return INSTANCE.createUniqueResultsQuery(parameters)
        }
    }
}