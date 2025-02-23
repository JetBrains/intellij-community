// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.idea.KotlinLanguage

interface ReferenceSearcher {
    fun findLocalUsages(element: PsiElement, scope: PsiElement): Collection<PsiReference>
    fun hasInheritors(`class`: PsiClass): Boolean
    fun hasOverrides(method: PsiMethod): Boolean
    fun findUsagesForExternalCodeProcessing(element: PsiElement, searchJava: Boolean, searchKotlin: Boolean): Collection<PsiReference>
}

object IdeaReferenceSearcher : ReferenceSearcher {
    override fun findLocalUsages(element: PsiElement, scope: PsiElement): Collection<PsiReference> =
        ReferencesSearch.search(element, LocalSearchScope(scope)).findAll()

    override fun hasInheritors(`class`: PsiClass) = ClassInheritorsSearch.search(`class`, false).asIterable().any()

    override fun hasOverrides(method: PsiMethod) = OverridingMethodsSearch.search(method, false).asIterable().any()

    override fun findUsagesForExternalCodeProcessing(
        element: PsiElement,
        searchJava: Boolean,
        searchKotlin: Boolean
    ): Collection<PsiReference> {
        val javaFileType = JavaLanguage.INSTANCE.associatedFileType.takeIf { searchJava }
        val kotlinFileType = KotlinLanguage.INSTANCE.associatedFileType.takeIf { searchKotlin }
        val fileTypes = listOfNotNull(javaFileType, kotlinFileType)

        val searchScope =
            GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.projectScope(element.project), *fileTypes.toTypedArray())
        return ReferencesSearch.search(element, searchScope).findAll()
    }
}
