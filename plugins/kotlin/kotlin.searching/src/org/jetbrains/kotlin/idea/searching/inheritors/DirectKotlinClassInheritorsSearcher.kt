// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.searching.inheritors

import com.intellij.model.search.Searcher
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.AbstractQuery
import com.intellij.util.Processor
import com.intellij.util.Query
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.stubindex.KotlinSuperClassIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasByExpansionShortNameIndex
import org.jetbrains.kotlin.psi.KtClassOrObject

internal class DirectKotlinClassInheritorsSearcher : Searcher<DirectKotlinClassInheritorsSearch.SearchParameters, PsiElement> {
    @RequiresReadLock
    override fun collectSearchRequest(parameters: DirectKotlinClassInheritorsSearch.SearchParameters): Query<out PsiElement>? {
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
                .mapNotNull { it.name }
                .onEach { names.add(it) }
                .forEach(::searchForTypeAliasesRecursively)
        }

        runReadAction { searchForTypeAliasesRecursively(baseClassName) }

        val basePointer = runReadAction {
            analyze(baseClass) {
                (baseClass.getSymbol() as? KtClassOrObjectSymbol)?.createPointer()
            }
        } ?: return null
        val noLibrarySourceScope = KotlinSourceFilterScope.projectFiles(scope, project)
        return object : AbstractQuery<PsiElement>() {
            override fun processResults(consumer: Processor<in PsiElement>): Boolean {
                return names.all { name -> runReadAction { processBaseName(name, consumer) } }
            }

            private fun processBaseName(name: String, consumer: Processor<in PsiElement>): Boolean {
                ProgressManager.checkCanceled()
                KotlinSuperClassIndex
                    .get(name, project, noLibrarySourceScope)
                    .asSequence()
                    .filter { isValidInheritor(it) }
                    .forEach { candidate ->
                        ProgressManager.checkCanceled()
                        if (!consumer.process(candidate)) {
                            return false
                        }
                    }
                return true
            }

            private fun isValidInheritor(ktClassOrObject: KtClassOrObject) : Boolean {
                ProgressManager.checkCanceled()
                analyze(ktClassOrObject) {
                    val baseSymbol = basePointer.restoreSymbol() ?: return false
                    val ktSymbol = ktClassOrObject.getSymbol() as? KtClassOrObjectSymbol ?: return false
                    if (!parameters.includeAnonymous && ktSymbol !is KtNamedSymbol) {
                        return false
                    }
                    return ktSymbol.isSubClassOf(baseSymbol)
                }
            }
        }
    }
}
