// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegateBase
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.refactoring.KotlinFirRefactoringsSettings
import org.jetbrains.kotlin.idea.refactoring.canDeleteElement
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty

class KotlinFirSafeDeleteProcessor : SafeDeleteProcessorDelegateBase() {
    override fun handlesElement(element: PsiElement?) = element.canDeleteElement()

    override fun findUsages(
        element: PsiElement,
        allElementsToDelete: Array<out PsiElement>,
        result: MutableList<UsageInfo>
    ): NonCodeUsageSearchInfo? {
        TODO("Not yet implemented")
    }

    override fun getElementsToSearch(
        element: PsiElement,
        module: Module?,
        allElementsToDelete: MutableCollection<PsiElement>
    ): MutableCollection<out PsiElement>? {
        return arrayListOf(element)
    }

    override fun getAdditionalElementsToDelete(
        element: PsiElement,
        allElementsToDelete: MutableCollection<PsiElement>,
        askUser: Boolean
    ): MutableCollection<PsiElement>? {
        TODO("Not yet implemented")
    }

    override fun findConflicts(element: PsiElement, allElementsToDelete: Array<out PsiElement>): MutableCollection<String>? {
        TODO("Not yet implemented")
    }

    override fun preprocessUsages(project: Project?, usages: Array<out UsageInfo>?): Array<UsageInfo>? {
        TODO("Not yet implemented")
    }

    override fun prepareForDeletion(element: PsiElement?) {
        TODO("Not yet implemented")
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