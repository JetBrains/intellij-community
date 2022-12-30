// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.stubindex.KotlinSuperClassIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasByExpansionShortNameIndex

internal class DirectKotlinClassInheritorsSearcher : QueryExecutorBase<KtClassOrObjectSymbol, DirectKotlinClassInheritorsSearch.SearchParameters>(true) {
    override fun processQuery(queryParameters: DirectKotlinClassInheritorsSearch.SearchParameters, consumer: Processor<in KtClassOrObjectSymbol>) {
        val baseClass = queryParameters.ktClass

        val baseClassName = baseClass.name ?: return

        val file = baseClass.containingFile

        val originalScope = queryParameters.searchScope
        val scope = originalScope as? GlobalSearchScope ?: GlobalSearchScope.fileScope(file)

        val names = mutableSetOf(baseClassName)
        val project = file.project

        fun searchForTypeAliasesRecursively(typeName: String) {
            ProgressManager.checkCanceled()
            KotlinTypeAliasByExpansionShortNameIndex
                .get(typeName, project, scope)
                .asSequence()
                .map { it.name }
                .filterNotNull()
                .filter { it !in names }
                .onEach { names.add(it) }
                .forEach(::searchForTypeAliasesRecursively)
        }

        searchForTypeAliasesRecursively(baseClassName)

        analyze(baseClass) {
            val baseSymbol = baseClass.getSymbol() as? KtClassOrObjectSymbol ?: return
            val noLibrarySourceScope = KotlinSourceFilterScope.projectFiles(scope, project)
            names.forEach { name ->
                ProgressManager.checkCanceled()
                KotlinSuperClassIndex
                    .get(name, project, noLibrarySourceScope).asSequence()
                    .map { ktClassOrObject ->
                        ProgressManager.checkCanceled()
                        ktClassOrObject.getSymbol()
                    }
                    .filter { ktSymbol ->
                        ProgressManager.checkCanceled()
                        if (!queryParameters.includeAnonymous && ktSymbol !is KtNamedSymbol) return@filter false
                        return@filter (ktSymbol as? KtClassOrObjectSymbol)?.isSubClassOf(baseSymbol) == true
                    }
                    .forEach { candidate ->
                        ProgressManager.checkCanceled()
                        consumer.process(candidate as KtClassOrObjectSymbol)
                    }
            }
        }
    }
}
