// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaUsualClassType
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.stubindex.KotlinSuperClassIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasByExpansionShortNameIndex
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry

internal class DirectKotlinClassInheritorsSearcher : Searcher<DirectKotlinClassInheritorsSearch.SearchParameters, PsiElement> {
    @RequiresReadLock
    override fun collectSearchRequest(parameters: DirectKotlinClassInheritorsSearch.SearchParameters): Query<out PsiElement>? {
        val baseClass = runReadAction { parameters.ktClass.originalElement } as? KtClass ?: return null

        val baseClassName = baseClass.name ?: return null

        val file = baseClass.containingFile

        val originalScope = parameters.searchScope
        val scope = originalScope as? GlobalSearchScope ?: GlobalSearchScope.fileScope(file)

        val names = mutableSetOf(baseClassName)
        val project = file.project

        fun searchForTypeAliasesRecursively(typeName: String) {
            ProgressManager.checkCanceled()
            KotlinTypeAliasByExpansionShortNameIndex[typeName, project, scope]
                .asSequence()
                .mapNotNull { it.name }
                .filter { names.add(it) }
                .forEach(::searchForTypeAliasesRecursively)
        }

        runReadAction { searchForTypeAliasesRecursively(baseClassName) }

        val basePointer = runReadAction {
            analyze(baseClass) {
                baseClass.namedClassSymbol?.createPointer()
            }
        } ?: return null

        val noLibrarySourceScope = KotlinSourceFilterScope.projectFiles(scope, project)
        return object : AbstractQuery<PsiElement>() {
            override fun processResults(consumer: Processor<in PsiElement>): Boolean {
                if (runReadAction { baseClass.isEnum() && !processEnumConstantsWithClassInitializers(consumer) }) return false
                return names.all { name -> runReadAction { processBaseName(name, consumer) } }
            }

            private fun processEnumConstantsWithClassInitializers(consumer: Processor<in PsiElement>) =
                baseClass.declarations.all { enumEntry ->
                    enumEntry !is KtEnumEntry || enumEntry.body == null || consumer.process(enumEntry)
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

            private fun isValidInheritor(ktClassOrObject: KtClassOrObject): Boolean {
                ProgressManager.checkCanceled()

                if (!parameters.includeLocal && ktClassOrObject.isLocal) {
                    return false
                }

                analyze(ktClassOrObject) {
                    val baseSymbol = basePointer.restoreSymbol() ?: return false
                    val ktSymbol = ktClassOrObject.classSymbol ?: return false
                    if (!parameters.includeAnonymous && ktSymbol !is KaNamedSymbol) {
                        return false
                    }

                    fun KaUsualClassType.classIdWithExpandedTypeAlias(): ClassId =
                        ((symbol as? KaTypeAliasSymbol)?.expandedType as? KaUsualClassType)?.classId ?: classId

                    return ktSymbol.superTypes.any { it is KaUsualClassType && (it.symbol == baseSymbol || it.classIdWithExpandedTypeAlias() == baseSymbol.classId) }
                }
            }
        }
    }
}
