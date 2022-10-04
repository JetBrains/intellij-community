// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search

import com.intellij.model.search.Searcher
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.Query
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.stubindex.KotlinSuperClassIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasByExpansionShortNameIndex

internal class DirectKotlinClassInheritorsSearcher : Searcher<DirectKotlinClassInheritorsSearch.SearchParameters, KtClassOrObjectSymbol> {
    @ApiStatus.OverrideOnly
    @RequiresReadLock
    override fun collectSearchRequest(parameters: DirectKotlinClassInheritorsSearch.SearchParameters): Query<out KtClassOrObjectSymbol>? {
        val baseClass = parameters.ktClass

        val baseClassName = baseClass.name ?: return null

        val file = baseClass.containingFile

        val originalScope = parameters.searchScope
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
            val baseSymbol = baseClass.getSymbol() as? KtClassOrObjectSymbol ?: return null
            val noLibrarySourceScope = KotlinSourceFilterScope.projectFiles(scope, project)
            return object : com.intellij.util.AbstractQuery<KtClassOrObjectSymbol>() {
                override fun processResults(@NotNull consumer: Processor<in KtClassOrObjectSymbol>): Boolean {
                    names.forEach { name ->
                        ProgressManager.checkCanceled()
                        KotlinSuperClassIndex
                            .get(name, project, noLibrarySourceScope).asSequence()
                            .map { ktClassOrObject ->
                                ProgressManager.checkCanceled()
                                analyze(ktClassOrObject) {
                                    val ktSymbol = ktClassOrObject.getSymbol() as? KtClassOrObjectSymbol ?: return@map null
                                    if (!parameters.includeAnonymous && ktSymbol !is KtNamedSymbol) return@map null
                                    return@map if (ktSymbol.isSubClassOf(baseSymbol)) ktSymbol else null
                                }
                            }
                            .forEach { candidate ->
                                ProgressManager.checkCanceled()
                                if (candidate != null && !consumer.process(candidate)) {
                                    return false
                                }
                            }
                    }
                    return true
                }
            }
        }
    }
}
