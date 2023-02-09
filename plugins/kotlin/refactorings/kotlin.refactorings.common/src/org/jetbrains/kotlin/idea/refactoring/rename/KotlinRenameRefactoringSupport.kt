// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
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

    fun getModuleNameSuffixForMangledName(mangledName: String): String?

    fun mangleInternalName(name: String, moduleName: String): String

    fun demangleInternalName(mangledName: String): String?

    fun actualsForExpected(declaration: KtDeclaration): Set<KtDeclaration>

    fun liftToExpected(declaration: KtDeclaration): KtDeclaration?

    fun getJvmName(element: PsiElement): String?

    fun isCompanionObjectClassReference(psiReference: PsiReference): Boolean

    fun shortenReferencesLater(element: KtElement)

    fun withExpectedActuals(classOrObject: KtDeclaration): List<KtDeclaration> {
        val expect = liftToExpected(classOrObject) ?: return listOf(classOrObject)
        val actuals = actualsForExpected(expect)
        return listOf(expect) + actuals
    }

    fun dropOverrideKeywordIfNecessary(element: KtNamedDeclaration)

    fun findAllOverridingMethods(psiMethod: PsiMethod, scope: SearchScope): List<PsiMethod>

    fun getJvmNamesForPropertyAccessors(element: PsiElement): Pair<String?, String?>

    /**
     * @return true if [element] is a light class for a regular Kotlin class (and not a facade class, for example).
     */
    fun isLightClassForRegularKotlinClass(element: KtLightClass): Boolean
}