// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inheritorsSearch

import com.intellij.model.search.Searcher
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import com.intellij.util.Query
import com.intellij.util.QueryFactory
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.asJava.toFakeLightClass
import org.jetbrains.kotlin.asJava.toLightClass

private val EVERYTHING_BUT_KOTLIN = object : QueryFactory<PsiClass, DirectClassInheritorsSearch.SearchParameters>() {
    init {
        DirectClassInheritorsSearch.EP_NAME.extensionList
            .filterNot { it::class.java.name.equals("org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinDirectInheritorsSearcher") }
            .forEach { registerExecutor(it) }
    }
}

internal class DirectKotlinClassDelegatedSearcher : Searcher<DirectKotlinClassInheritorsSearch.SearchParameters, PsiElement> {
    @RequiresReadLock
    override fun collectSearchRequest(parameters: DirectKotlinClassInheritorsSearch.SearchParameters): Query<out PsiElement> {
        val baseClass = parameters.ktClass
        val lightClass = baseClass.toLightClass() ?: baseClass.toFakeLightClass()
        val params =
            DirectClassInheritorsSearch.SearchParameters(lightClass, parameters.searchScope, parameters.includeAnonymous, true)
        return EVERYTHING_BUT_KOTLIN.createQuery(params)
    }
}