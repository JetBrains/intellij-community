// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Service for various functionality which have different implementation in K1 and K2 plugin
 * and which is used in common Rename Refactoring code
 */
interface KotlinRenameRefactoringSupport {
    companion object {
        @JvmStatic
        fun getInstance(): KotlinRenameRefactoringSupport = service()
    }

    fun processForeignUsages(element: PsiElement, newName: String, usages: Array<UsageInfo>, fallbackHandler: (UsageInfo) -> Unit)

    fun prepareForeignUsagesRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope)

    fun checkRedeclarations(
        declaration: KtNamedDeclaration,
        newName: String,
        result: MutableList<UsageInfo>
    )

    fun checkOriginalUsagesRetargeting(
        declaration: KtNamedDeclaration,
        newName: String,
        originalUsages: MutableList<UsageInfo>,
        newUsages: MutableList<UsageInfo>
    )

    fun checkNewNameUsagesRetargeting(
        declaration: KtNamedDeclaration,
        newName: String,
        newUsages: MutableList<UsageInfo>
    )

    fun checkAccidentalPropertyOverrides(
        declaration: KtNamedDeclaration,
        newName: String,
        result: MutableList<UsageInfo>
    )

    fun getAllOverridenFunctions(function: KtNamedFunction): List<PsiElement>
}