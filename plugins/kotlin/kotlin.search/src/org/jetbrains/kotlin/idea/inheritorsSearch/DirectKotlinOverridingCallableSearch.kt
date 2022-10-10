// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inheritorsSearch

import com.intellij.model.search.SearchService
import com.intellij.model.search.Searcher
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.util.*
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.inheritorsSearch.DirectKotlinOverridingCallableSearch.SearchParameters
import org.jetbrains.kotlin.idea.search.ideaExtensions.JavaOverridingMethodsSearcherFromKotlinParameters
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

object DirectKotlinOverridingCallableSearch {

    data class SearchParameters(
        val ktCallableDeclaration: KtCallableDeclaration,
        val searchScope: SearchScope
    ) : com.intellij.model.search.SearchParameters<PsiElement> {
        override fun getProject(): Project {
            return ktCallableDeclaration.project
        }

        override fun areValid(): Boolean {
            return ktCallableDeclaration.isValid
        }
    }

    fun search(ktFunction: KtCallableDeclaration): Query<PsiElement> {
        return search(ktFunction, ktFunction.useScope)
    }

    fun search(ktFunction: KtCallableDeclaration, searchScope: SearchScope): Query<PsiElement> {
        return search(SearchParameters(ktFunction, searchScope))
    }

    fun search(parameters: SearchParameters): Query<PsiElement> {
        return SearchService.getInstance().searchParameters(parameters)
    }

}

class DirectKotlinOverridingMethodSearcher : Searcher<SearchParameters, PsiElement> {
    @RequiresReadLock
    override fun collectSearchRequest(parameters: SearchParameters): Query<out PsiElement>? {
        val klass = parameters.ktCallableDeclaration.containingClassOrObject
        if (klass !is KtClass) return null
        
        return DirectKotlinClassInheritorsSearch.search(klass, parameters.searchScope)
            .flatMapping { ktClassOrObject ->
                if (ktClassOrObject !is KtClassOrObject) EmptyQuery.getEmptyQuery()
                else object : AbstractQuery<PsiElement>() {
                    override fun processResults(consumer: Processor<in PsiElement>): Boolean {
                        val superFunction = analyze(parameters.ktCallableDeclaration) {
                            parameters.ktCallableDeclaration.getSymbol()
                        }
                        analyze(ktClassOrObject) {
                            (ktClassOrObject.getSymbol() as KtClassOrObjectSymbol).getDeclaredMemberScope()
                                .getCallableSymbols { it == parameters.ktCallableDeclaration.nameAsName }
                                .forEach { overridingSymbol ->
                                    val function = overridingSymbol.psi
                                    if (function != null && 
                                        overridingSymbol.getAllOverriddenSymbols().any { it == superFunction } && 
                                        !consumer.process(function)) {
                                        return false
                                    }
                                }
                            return true
                        }
                    }
                }
            }
    }
}

private val oldSearchers = setOf(
    "org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinOverridingMethodsWithFlexibleTypesSearcher",
    "org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinOverridingMethodsWithGenericsSearcher"
)
private val EVERYTHING_BUT_KOTLIN = object : QueryFactory<PsiMethod, OverridingMethodsSearch.SearchParameters>() {
    init {
        OverridingMethodsSearch.EP_NAME.extensionList
            .filterNot { oldSearchers.contains(it::class.java.name) }
            .forEach { registerExecutor(it) }
    }
}

internal class DirectKotlinOverridingMethodDelegatedSearcher : Searcher<SearchParameters, PsiElement> {
    @RequiresReadLock
    override fun collectSearchRequest(parameters: SearchParameters): Query<out PsiElement> {
        val baseFunction = parameters.ktCallableDeclaration
        val methods = baseFunction.toLightMethods()

        val queries = methods.map { it ->
            EVERYTHING_BUT_KOTLIN.createQuery(JavaOverridingMethodsSearcherFromKotlinParameters(it, parameters.searchScope, false))
        }

        return object : AbstractQuery<PsiElement>() {
            override fun processResults(consumer: Processor<in PsiElement>): Boolean {
                return queries.all { it.forEach(consumer) }
            }
        }
    }
}
