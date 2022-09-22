// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.safeDelete.*
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo
import com.intellij.refactoring.util.RefactoringDescriptionLocation
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import com.intellij.util.containers.map2Array
import org.jetbrains.kotlin.analysis.api.analyzeInModalWindow
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithModality
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.refactoring.KotlinFirRefactoringsSettings
import org.jetbrains.kotlin.idea.refactoring.KotlinK2RefactoringsBundle
import org.jetbrains.kotlin.idea.refactoring.canDeleteElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex

class KotlinFirSafeDeleteProcessor : SafeDeleteProcessorDelegateBase() {
    override fun handlesElement(element: PsiElement?) = element.canDeleteElement()

    override fun findUsages(
        element: PsiElement,
        allElementsToDelete: Array<out PsiElement>,
        result: MutableList<UsageInfo>
    ): NonCodeUsageSearchInfo {
        val isInside: (t: PsiElement) -> Boolean =
            { allElementsToDelete.any { elementToDelete -> SafeDeleteProcessor.isInside(it, elementToDelete) } }
        if (element is KtDeclaration) {
            ReferencesSearch.search(element).forEach(Processor {
                val e = it.element
                if (!isInside(e)) {
                    if (e.getNonStrictParentOfType<KtValueArgumentName>() != null) {
                        //named argument would be deleted with argument
                        return@Processor true
                    }
                    val importDirective = e.getNonStrictParentOfType<KtImportDirective>()
                    result.add(SafeDeleteReferenceSimpleDeleteUsageInfo(importDirective ?: e, element, importDirective != null))
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
                    val referencedElement = reference.element

                    val argList = referencedElement.getNonStrictParentOfType<KtUserType>()?.typeArgumentList
                        ?: referencedElement.getNonStrictParentOfType<KtCallExpression>()?.typeArgumentList

                    if (argList != null) {
                        val projections = argList.arguments
                        if (parameterIndex < projections.size) {
                            result.add(SafeDeleteTypeArgumentUsageInfo(projections[parameterIndex], element))
                        }
                    } else {
                        JavaSafeDeleteProcessor.createJavaTypeParameterUsageInfo(element, parameterList.size, parameterIndex, reference)
                            ?.let { result.add(it) }
                    }
                }
            }
        }
        
        if (element is KtParameter) {
            val function = element.getNonStrictParentOfType<KtFunction>()
            if (function != null) {
                val parameterIndexAsJavaCall = element.parameterIndex() + if (function.receiverTypeReference != null) 1 else 0
                ReferencesSearch.search(function).forEach(Processor {
                    JavaSafeDeleteDelegate.EP.forLanguage(it.element.language)
                        ?.createUsageInfoForParameter(it, result, element, parameterIndexAsJavaCall, element.isVarArg)
                    return@Processor true
                })

                if (function is KtPrimaryConstructor) {
                    ReferencesSearch.search(function.getContainingClassOrObject()).forEach(Processor {
                        JavaSafeDeleteDelegate.EP.forLanguage(it.element.language)
                            ?.createUsageInfoForParameter(it, result, element, parameterIndexAsJavaCall, element.isVarArg)
                        return@Processor true
                    })
                }
            }
        }
        
        return NonCodeUsageSearchInfo(isInside, element)
    }

    override fun getElementsToSearch(
        element: PsiElement,
        module: Module?,
        allElementsToDelete: MutableCollection<PsiElement>
    ): MutableCollection<out PsiElement> {
        return arrayListOf(element)
    }

    override fun getAdditionalElementsToDelete(
        element: PsiElement,
        allElementsToDelete: MutableCollection<PsiElement>,
        askUser: Boolean
    ): MutableCollection<PsiElement>? {
        return null
    }

    override fun findConflicts(element: PsiElement, allElementsToDelete: Array<out PsiElement>): MutableCollection<String>? {
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

    override fun preprocessUsages(project: Project?, usages: Array<out UsageInfo>?): Array<UsageInfo>? {
        return usages?.map2Array { it }
    }

    override fun prepareForDeletion(element: PsiElement?) {
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

class SafeDeleteTypeArgumentUsageInfo(
    projection: KtTypeProjection,
    referenceElement: PsiElement
) : SafeDeleteReferenceSimpleDeleteUsageInfo(projection, referenceElement, true) {
    override fun deleteElement() {
        val e = element
        if (e != null) {
            deleteSeparatingComma(e)
            deleteBracesAroundEmptyList(e)

            e.delete()
        }
    }
}

private fun deleteBracesAroundEmptyList(element: PsiElement?) {
    val nextSibling = PsiTreeUtil.skipWhitespacesAndCommentsForward(element)
    val prevSibling = PsiTreeUtil.skipWhitespacesAndCommentsBackward(element)
    if (nextSibling != null && nextSibling.elementType == KtTokens.GT &&
        prevSibling != null && prevSibling.elementType == KtTokens.LT) {
         //keep comments
        nextSibling.delete()
        prevSibling.delete()
    }
}


private fun deleteSeparatingComma(e: PsiElement?) {
    val nextSibling = PsiTreeUtil.skipWhitespacesAndCommentsForward(e)
    if (nextSibling != null && nextSibling.elementType == KtTokens.COMMA) {
        nextSibling.delete()
    } else {
        val prevSibling = PsiTreeUtil.skipWhitespacesAndCommentsBackward(e)
        if (prevSibling != null && prevSibling.elementType == KtTokens.COMMA) {
            prevSibling.delete()
        }
    }
}