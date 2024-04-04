// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.searching.usages.handlers

import com.intellij.find.findUsages.AbstractFindUsagesDialog
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.find.findUsages.JavaFindUsagesHelper
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usageView.UsageInfo
import com.intellij.util.FilteredQuery
import com.intellij.util.Processor
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinClassFindUsagesOptions
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFindUsagesHandlerFactory
import org.jetbrains.kotlin.idea.base.searching.usages.dialogs.KotlinFindClassUsagesDialog
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport.Companion.isConstructorUsage
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport.Companion.processCompanionObjectInternalReferences
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchOptions
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchParameters
import org.jetbrains.kotlin.idea.search.isImportUsage
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.contains
import org.jetbrains.kotlin.psi.psiUtil.effectiveDeclarations
import java.util.*

class KotlinFindClassUsagesHandler(
    ktClass: KtClassOrObject,
    factory: KotlinFindUsagesHandlerFactory
) : KotlinFindUsagesHandler<KtClassOrObject>(ktClass, factory) {
    override fun getFindUsagesDialog(
        isSingleFile: Boolean, toShowInNewTab: Boolean, mustOpenInNewTab: Boolean
    ): AbstractFindUsagesDialog {
        return KotlinFindClassUsagesDialog(
            getElement(),
            project,
            factory.findClassOptions,
            toShowInNewTab,
            mustOpenInNewTab,
            isSingleFile,
            this
        )
    }

    override fun createSearcher(
        element: PsiElement,
        processor: Processor<in UsageInfo>,
        options: FindUsagesOptions
    ): Searcher {
        return MySearcher(element, processor, options)
    }

    private class MySearcher(
        element: PsiElement, processor: Processor<in UsageInfo>, options: FindUsagesOptions
    ) : Searcher(element, processor, options) {

        private val kotlinOptions = options as KotlinClassFindUsagesOptions
        private val referenceProcessor = createReferenceProcessor(processor)

        override fun buildTaskList(forHighlight: Boolean): Boolean {
            val classOrObject = element as KtClassOrObject

            if (kotlinOptions.isUsages || kotlinOptions.searchConstructorUsages) {
                processClassReferencesLater(classOrObject)
            }

            if (kotlinOptions.isFieldsUsages || kotlinOptions.isMethodsUsages) {
                processMemberReferencesLater(classOrObject)
            }

            if (kotlinOptions.isUsages && classOrObject is KtObjectDeclaration && classOrObject.isCompanion() && classOrObject in options.searchScope) {
                if (!processCompanionObjectInternalReferences(classOrObject, referenceProcessor)) return false
            }

            if (kotlinOptions.searchConstructorUsages) {
                for (constructor in classOrObject.allConstructors) {
                    addTask { ReferencesSearch.search(constructor, options.searchScope).forEach(referenceProcessor) }
                }
            }

            if (kotlinOptions.isDerivedClasses || kotlinOptions.isDerivedInterfaces) {
                processInheritorsLater()
            }

            return true
        }

        private fun processInheritorsLater() {
            addTask {
                val searchInheritors = KotlinFindUsagesSupport.searchInheritors(element, options.searchScope)
                searchInheritors.all { e ->
                    runReadAction {
                        if (!e.isValid) return@runReadAction false
                        val isInterface = (e as? KtClass)?.isInterface() ?: (e as? PsiClass)?.isInterface ?: false
                        when {
                            isInterface && kotlinOptions.isDerivedInterfaces || !isInterface && kotlinOptions.isDerivedClasses ->
                                processUsage(processor, e.navigationElement)

                            else -> true
                        }
                    }
                }
            }
        }

        private fun processClassReferencesLater(classOrObject: KtClassOrObject) {
            val searchParameters = KotlinReferencesSearchParameters(
                classOrObject,
                scope = options.searchScope,
                kotlinOptions = KotlinReferencesSearchOptions(
                    acceptCompanionObjectMembers = true,
                    searchForExpectedUsages = kotlinOptions.searchExpected
                )
            )
            var usagesQuery = ReferencesSearch.search(searchParameters)

            if (kotlinOptions.isSkipImportStatements) {
                usagesQuery = FilteredQuery(usagesQuery) { !it.isImportUsage() }
            }

            if (!kotlinOptions.searchConstructorUsages) {
                usagesQuery = FilteredQuery(usagesQuery) { !it.isConstructorUsage(classOrObject) }
            } else if (!options.isUsages && classOrObject !is KtObjectDeclaration && !(classOrObject as KtClass).isEnum()) {
                usagesQuery = FilteredQuery(usagesQuery) { it.isConstructorUsage(classOrObject) }
            }
            addTask { usagesQuery.forEach(referenceProcessor) }
        }

        private fun processMemberReferencesLater(classOrObject: KtClassOrObject) {
            for (declaration in classOrObject.effectiveDeclarations()) {
                if ((declaration is KtNamedFunction && kotlinOptions.isMethodsUsages) ||
                    ((declaration is KtProperty || declaration is KtParameter) && kotlinOptions.isFieldsUsages)
                ) {
                    addTask { ReferencesSearch.search(declaration, options.searchScope).forEach(referenceProcessor) }
                }
            }
        }
    }

    override fun getStringsToSearch(element: PsiElement): Collection<String> {
        val psiClass = when (element) {
            is PsiClass -> element
            is KtClassOrObject -> getElement().toLightClass()
            else -> null
        } ?: return Collections.emptyList()

        return JavaFindUsagesHelper.getElementNames(psiClass)
    }

    override fun isSearchForTextOccurrencesAvailable(psiElement: PsiElement, isSingleFile: Boolean): Boolean {
        return !isSingleFile
    }

    override fun getFindUsagesOptions(dataContext: DataContext?): FindUsagesOptions {
        return factory.findClassOptions
    }
}
