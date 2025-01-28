// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.searching.inheritors

import com.intellij.model.search.SearchService
import com.intellij.model.search.Searcher
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.util.*
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.analysis.api.utils.getSymbolContainingMemberDeclarations
import org.jetbrains.kotlin.idea.search.ideaExtensions.JavaOverridingMethodsSearcherFromKotlinParameters
import org.jetbrains.kotlin.idea.searching.inheritors.DirectKotlinOverridingCallableSearch.SearchParameters
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
            return runReadAction { ktCallableDeclaration.project }
        }

        override fun areValid(): Boolean {
            return ktCallableDeclaration.isValid
        }
    }

    fun search(ktFunction: KtCallableDeclaration): Query<out PsiElement> {
        return search(ktFunction, runReadAction { ktFunction.useScope })
    }

    fun search(ktFunction: KtCallableDeclaration, searchScope: SearchScope): Query<out PsiElement> {
        return search(SearchParameters(ktFunction, searchScope))
    }

    fun search(parameters: SearchParameters): Query<out PsiElement> {
        return SearchService.getInstance().searchParameters(parameters)
    }

}

class DirectKotlinOverridingMethodSearcher : Searcher<SearchParameters, PsiElement> {
    @RequiresReadLock
    override fun collectSearchRequest(parameters: SearchParameters): Query<out PsiElement>? {
        val ktCallableDeclaration = runReadAction {
            parameters.ktCallableDeclaration.originalElement
        } as? KtCallableDeclaration ?: return null

        val ktCallableDeclarationName = ktCallableDeclaration.nameAsName ?: return null
        val klass = ktCallableDeclaration.containingClassOrObject
        if (klass !is KtClass) return null

        val superDeclarationPointer = runReadAction { ktCallableDeclaration.createSmartPointer() }

        return CollectionQuery(
            klass.findAllInheritors(parameters.searchScope).mapNotNull { it.unwrapped as? KtClassOrObject }.toList()
        ).flatMapping { ktClassOrObject ->
            val ktClassOrObjectPointer = runReadAction { ktClassOrObject.createSmartPointer() }
            object : AbstractQuery<PsiElement>() {
                override fun processResults(consumer: Processor<in PsiElement>): Boolean = runReadAction {
                    val classOrObject = ktClassOrObjectPointer.element ?: return@runReadAction true

                    analyze(classOrObject) {
                        val superFunction = superDeclarationPointer.element ?: return@runReadAction false
                        val symbolWithMembers = classOrObject.symbol.getSymbolContainingMemberDeclarations() ?: return@runReadAction true

                        symbolWithMembers.declaredMemberScope
                            .callables(ktCallableDeclarationName)
                            .all { overridingSymbol ->
                                val function = overridingSymbol.psi
                                if (function != null && overridingSymbol.directlyOverriddenSymbols.any { it.psi == superFunction }) {
                                    consumer.process(function)
                                } else {
                                    true
                                }
                            }
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
    override fun collectSearchRequests(parameters: SearchParameters): Collection<Query<out PsiElement>> {
        val baseFunction = parameters.ktCallableDeclaration
        val methods = baseFunction.toLightMethods()

        return methods.map<PsiMethod, Query<PsiElement>> { lightMethod ->
            EVERYTHING_BUT_KOTLIN.createQuery(JavaOverridingMethodsSearcherFromKotlinParameters(lightMethod, parameters.searchScope, true))
                .flatMapping { psiMethod ->
                    object : AbstractQuery<PsiElement>() {
                        override fun processResults(consumer: Processor<in PsiElement>): Boolean = runReadAction {
                            if (psiMethod.hierarchicalMethodSignature.superSignatures.any { hs -> hs.method.isEquivalentTo(lightMethod) }) {
                                consumer.process(psiMethod)
                            } else {
                                true
                            }
                        }
                    }
                }
        }
    }
}
