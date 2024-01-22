// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.safeDelete.JavaSafeDeleteDelegate
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegateBase
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo
import com.intellij.refactoring.util.RefactoringDescriptionLocation
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import com.intellij.util.containers.map2Array
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithModality
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.KotlinFirRefactoringsSettings
import org.jetbrains.kotlin.idea.k2.refactoring.KotlinK2RefactoringsBundle
import org.jetbrains.kotlin.idea.k2.refactoring.canDeleteElement
import org.jetbrains.kotlin.idea.k2.refactoring.checkSuperMethods
import org.jetbrains.kotlin.idea.refactoring.*
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinRenameRefactoringSupport
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.actualsForExpected
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchOptions
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchParameters
import org.jetbrains.kotlin.idea.searching.inheritors.DirectKotlinClassInheritorsSearch
import org.jetbrains.kotlin.idea.searching.inheritors.findAllOverridings
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import java.util.*

class KotlinFirSafeDeleteProcessor : SafeDeleteProcessorDelegateBase() {
    override fun handlesElement(element: PsiElement?) = element.canDeleteElement()

    override fun findUsages(
        element: PsiElement,
        allElementsToDelete: Array<out PsiElement>,
        result: MutableList<in UsageInfo>
    ): NonCodeUsageSearchInfo {

        fun isInside (t: PsiElement, ancestors : Array<out PsiElement>) : Boolean =
             ancestors.any { elementToDelete -> SafeDeleteProcessor.isInside(t, elementToDelete) } 
        
        val isInside: (t: PsiElement) -> Boolean = { isInside(it, allElementsToDelete) }

        val additionalElementsToDelete = arrayListOf<PsiElement>()
        if (element is KtNamedFunction || element is KtProperty) {
            findFunctionUsages(element as KtCallableDeclaration, allElementsToDelete, isInside, additionalElementsToDelete, result)
        }

        if (element is KtDeclaration) {
            val additionalElementsToDeleteArray = additionalElementsToDelete.toTypedArray()
            //group declarations into expected to receive conflicts once per expected/actuals group
            val expected = KotlinRenameRefactoringSupport.getInstance().liftToExpected(element) ?: element
            ReferencesSearch.search(element).forEach(Processor {
                val e = it.element
                if (!isInside(e) && !isInside(e, additionalElementsToDeleteArray)) {
                    if (e.getNonStrictParentOfType<KtValueArgumentName>() != null) {
                        //named argument would be deleted with argument
                        return@Processor true
                    }
                    val importDirective = e.getNonStrictParentOfType<KtImportDirective>()
                    result.add(SafeDeleteReferenceSimpleDeleteUsageInfo(importDirective ?: e, expected, importDirective != null))
                }
                return@Processor true
            })
        }
        
        if (element is KtTypeParameter) {
            val owner = element.getNonStrictParentOfType<KtTypeParameterListOwner>()
            if (owner != null) {
                val parameterList = owner.typeParameters
                val parameterIndex = parameterList.indexOf(element)
                for (reference in ReferencesSearch.search(owner)) {
                    JavaSafeDeleteDelegate.EP.forLanguage(reference.element.language)?.createJavaTypeParameterUsageInfo(
                        reference,
                        result,
                        element,
                        parameterList.size,
                        parameterIndex
                    )
                }
            }
        }
        
        if (element is KtParameter) {
            val function = element.getNonStrictParentOfType<KtFunction>()
            if (function != null) {
                val parameterIndexAsJavaCall = element.parameterIndex() + if (function.receiverTypeReference != null) 1 else 0
                findCallArgumentsToDelete(result, element, parameterIndexAsJavaCall, function)
            }
        }
        
        return NonCodeUsageSearchInfo(isInside, element)
    }

    private fun findFunctionUsages(
        element: KtCallableDeclaration,
        allElementsToDelete: Array<out PsiElement>,
        isInside: (t: PsiElement) -> Boolean,
        additionalElementsToDelete: ArrayList<PsiElement>,
        result: MutableList<in UsageInfo>
    ) {
        val overridden = arrayListOf<PsiElement>()
        val containingClass = element.containingClass()
        if (containingClass != null) {
            analyze(containingClass) {
                val elementClassSymbol = containingClass.getSymbol() as KtClassOrObjectSymbol

                fun isMultipleInheritance(function: KtSymbol): Boolean {
                    val superMethods = (function as? KtCallableSymbol)?.getDirectlyOverriddenSymbols() ?: return false
                    return superMethods.any {
                        val superClassSymbol = it.getContainingSymbol() as? KtClassOrObjectSymbol ?: return@any false
                        val superMethod = it.psi ?: return@any false
                        return@any !isInside(superMethod) && !superClassSymbol.isSubClassOf(elementClassSymbol)
                    }
                }

                overridden.add(element)
                element.findAllOverridings().forEach { m ->
                    val original = m.unwrapped
                    if (original != null && !allElementsToDelete.contains(original)) {
                        val oSymbol = when (original) {
                            is KtDeclaration -> original.getSymbol()
                            is PsiMember -> original.getCallableSymbol()
                            else -> null
                        }

                        if (oSymbol != null && isMultipleInheritance(oSymbol)) {
                            return@forEach
                        }

                        overridden.add(original)
                    }
                }
            }
        }

        for (overriddenFunction in overridden) {
            if (ReferencesSearch.search(overriddenFunction).forEach(Processor {
                    val place = it.element
                    return@Processor isInside(place)
                })) {
                additionalElementsToDelete.add(overriddenFunction)
                result.add(SafeDeleteReferenceSimpleDeleteUsageInfo(overriddenFunction, element, true))
            }
            else {
                JavaSafeDeleteDelegate.EP.forLanguage(overriddenFunction.language)?.createCleanupOverriding(overriddenFunction, allElementsToDelete, result)
            }
        }
    }

    private fun findCallArgumentsToDelete(
        result: MutableList<in UsageInfo>,
        element: KtParameter,
        parameterIndexAsJavaCall: Int,
        ktElement: KtElement
    ) {
        if (ktElement is KtConstructor<*>) {
            val containingClass = ktElement.containingClass() ?: return

            val directInheritors = mutableListOf<KtElement>()
            DirectKotlinClassInheritorsSearch.search(containingClass).forEach { el ->
                if (el !is KtElement) {
                    val lightMethod = ktElement.toLightMethods().filterIsInstance<KtLightMethod>().firstOrNull() ?: return
                    MethodReferencesSearch.search(lightMethod, lightMethod.useScope, true).forEach(Processor {
                        JavaSafeDeleteDelegate.EP.forLanguage(it.element.language)
                            ?.createUsageInfoForParameter(it, result, element, parameterIndexAsJavaCall, element.isVarArg)
                        return@Processor true
                    })
                    return
                }
                directInheritors.add(el)
            }

            fun processDelegatingReferences(ktClass: KtClass) {
                ktClass.secondaryConstructors.forEach { c ->
                    if (c != ktElement) {
                        val delegationCall = c.getDelegationCallOrNull()
                        val reference = delegationCall?.calleeExpression?.mainReference
                        if (reference != null && reference.resolve() == ktElement) {
                            JavaSafeDeleteDelegate.EP.forLanguage(reference.element.language)
                                ?.createUsageInfoForParameter(reference, result, element, parameterIndexAsJavaCall, element.isVarArg)
                        }
                    }
                }
            }

            processDelegatingReferences(containingClass)
            for (inheritor in directInheritors) {
                (inheritor as? KtClass)?.let { processDelegatingReferences(it) }
            }

            //to find constructor calls in java, one needs to perform class search
            findCallArgumentsToDelete(result, element, parameterIndexAsJavaCall, containingClass)
        }


        ReferencesSearch.search(ktElement).forEach(Processor {
            JavaSafeDeleteDelegate.EP.forLanguage(it.element.language)
                ?.createUsageInfoForParameter(it, result, element, parameterIndexAsJavaCall, element.isVarArg)
            return@Processor true
        })
    }

    override fun getElementsToSearch(
      element: PsiElement,
      module: Module?,
      allElementsToDelete: Collection<PsiElement>
    ): Collection<PsiElement> {
        return ActionUtil.underModalProgress(element.project, KotlinBundle.message("progress.title.searching.for.expected.actual")) {
            val mapToExpected: (PsiElement) -> List<PsiElement> = { e ->
                (e as? KtDeclaration)?.let { KotlinRenameRefactoringSupport.getInstance().withExpectedActuals(it) } ?: listOf(e)
            }
            when (element) {
                is KtParameter -> {
                    val parametersToSearch = getParametersToSearch(element)
                    parametersToSearch.flatMap(mapToExpected)
                }

                is KtNamedFunction, is KtProperty -> {
                    if (isUnitTestMode()) mapToExpected(element)
                    else checkSuperMethods(element as KtDeclaration, allElementsToDelete, RefactoringBundle.message("to.refactor")).flatMap(
                        mapToExpected
                    )
                }

                else -> mapToExpected(element)
            }
        }
    }

    override fun getAdditionalElementsToDelete(
        element: PsiElement,
        allElementsToDelete: Collection<PsiElement>,
        askUser: Boolean
    ): Collection<PsiElement>? {
        return null
    }

    override fun findConflicts(element: PsiElement, allElementsToDelete: Array<out PsiElement>): Collection<String>? {
        if (element is KtNamedFunction || element is KtProperty) {
            val ktClass = element.getNonStrictParentOfType<KtClass>()
            if (ktClass == null || ktClass.body != element.parent) return null

            val modifierList = ktClass.modifierList
            if (modifierList != null && modifierList.hasModifier(KtTokens.ABSTRACT_KEYWORD)) return null

            return analyzeInModalWindow(element as KtDeclaration, RefactoringBundle.message("detecting.possible.conflicts")) {
                (element.getSymbol() as? KtCallableSymbol)?.getAllOverriddenSymbols()
                    ?.asSequence()
                    ?.filter { (it as? KtSymbolWithModality)?.modality == Modality.ABSTRACT }
                    ?.mapNotNull { it.psi }
                    ?.mapTo(ArrayList()) {
                        KotlinK2RefactoringsBundle.message(
                            "safe.delete.implements.conflict.message", 
                            ElementDescriptionUtil.getElementDescription(element, RefactoringDescriptionLocation.WITH_PARENT), 
                            ElementDescriptionUtil.getElementDescription(it, RefactoringDescriptionLocation.WITH_PARENT)
                        )
                    }
            }
        }
        return null
    }

    override fun preprocessUsages(project: Project, usages: Array<out UsageInfo>): Array<UsageInfo> {
        return usages.map2Array { it }
    }

    override fun prepareForDeletion(element: PsiElement) {
        when (element) {
            is KtTypeParameter -> {
                deleteSeparatingComma(element)
                deleteBracesAroundEmptyList(element)
            }

            is KtParameter -> {
                deleteSeparatingComma(element)
            }
        }
    }


    override fun isToSearchInComments(element: PsiElement?): Boolean {
        val settings = KotlinFirRefactoringsSettings.instance
        when (element) {
            is KtClass -> return settings.RENAME_SEARCH_IN_COMMENTS_FOR_CLASS
            is KtFunction -> return settings.RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION
            is KtProperty -> return settings.RENAME_SEARCH_IN_COMMENTS_FOR_PROPERTY
            else -> return false
        }
    }

    override fun setToSearchInComments(element: PsiElement?, enabled: Boolean) {
        val settings = KotlinFirRefactoringsSettings.instance
        when (element) {
            is KtClass -> settings.RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = enabled
            is KtFunction -> settings.RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION = enabled
            is KtProperty -> settings.RENAME_SEARCH_IN_COMMENTS_FOR_PROPERTY = enabled
        }
    }

    override fun isToSearchForTextOccurrences(element: PsiElement?): Boolean {
        val settings = KotlinFirRefactoringsSettings.instance
        when (element) {
            is KtClass -> return settings.RENAME_SEARCH_FOR_TEXT_FOR_CLASS
            is KtFunction -> return settings.RENAME_SEARCH_FOR_TEXT_FOR_FUNCTION
            is KtProperty -> return settings.RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE
            else -> return false
        }
    }

    override fun setToSearchForTextOccurrences(element: PsiElement?, enabled: Boolean) {
        val settings = KotlinFirRefactoringsSettings.instance
        when (element) {
            is KtClass -> settings.RENAME_SEARCH_FOR_TEXT_FOR_CLASS = enabled
            is KtFunction -> settings.RENAME_SEARCH_FOR_TEXT_FOR_FUNCTION = enabled
            is KtProperty -> settings.RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE = enabled
        }
    }
}
