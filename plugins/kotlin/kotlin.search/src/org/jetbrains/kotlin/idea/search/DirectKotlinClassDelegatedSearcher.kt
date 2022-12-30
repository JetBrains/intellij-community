// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiClass
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import com.intellij.util.Processor
import com.intellij.util.QueryFactory
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.asJava.toFakeLightClass
import org.jetbrains.kotlin.asJava.toLightClass

private val EVERYTHING_BUT_KOTLIN = object : QueryFactory<PsiClass, DirectClassInheritorsSearch.SearchParameters>() {
    init {
        DirectClassInheritorsSearch.EP_NAME.extensionList
            .filter { !(it::class.java.name.equals("org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinDirectInheritorsSearcher")) }
            .forEach { registerExecutor(it) }
    }
} 

internal class DirectKotlinClassDelegatedSearcher : QueryExecutorBase<KtClassOrObjectSymbol, DirectKotlinClassInheritorsSearch.SearchParameters>(true) {
    override fun processQuery(
        queryParameters: DirectKotlinClassInheritorsSearch.SearchParameters,
        consumer: Processor<in KtClassOrObjectSymbol>
    ) {
        val baseClass = queryParameters.ktClass
        val lightClass = baseClass.toLightClass() ?: baseClass.toFakeLightClass()
        val params = DirectClassInheritorsSearch.SearchParameters(lightClass, queryParameters.searchScope, queryParameters.includeAnonymous, true)
        analyze(baseClass) {
            EVERYTHING_BUT_KOTLIN.createQuery(params).forEach {
                it.getNamedClassSymbol()?.let { symbol -> consumer.process(symbol) }
            }
        }
    }
}