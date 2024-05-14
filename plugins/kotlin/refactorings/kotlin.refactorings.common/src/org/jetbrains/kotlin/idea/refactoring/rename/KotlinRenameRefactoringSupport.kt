// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
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

    fun checkUsagesRetargeting(declaration: KtNamedDeclaration, newName: String, originalUsages: MutableList<UsageInfo>, newUsages: MutableList<UsageInfo>)

    fun getAllOverridenFunctions(function: KtNamedFunction): List<PsiElement>

    fun getModuleNameSuffixForMangledName(mangledName: String): String? {
        val indexOfDollar = mangledName.indexOf('$')
        return if (indexOfDollar >= 0) mangledName.substring(indexOfDollar + 1) else null
    }

    fun mangleInternalName(name: String, moduleName: String): String

    fun demangleInternalName(mangledName: String): String?

    fun getJvmName(element: PsiElement): String?

    fun isCompanionObjectClassReference(psiReference: PsiReference): Boolean

    fun shortenReferencesLater(element: KtElement)

    fun overridesNothing(declaration: KtCallableDeclaration): Boolean {
        if (!declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return false

        @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class)
        allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                analyze(declaration) {

                    val declarationSymbol = declaration.symbol as? KaCallableSymbol ?: return false

                    val callableSymbol = when (declarationSymbol) {
                        is KaValueParameterSymbol -> declarationSymbol.generatedPrimaryConstructorProperty ?: return false
                        else -> declarationSymbol
                    }
                    return !callableSymbol.directlyOverriddenSymbols.iterator().hasNext()
                }
            }
        }
    }

    fun dropOverrideKeywordIfNecessary(element: KtNamedDeclaration)

    fun findAllOverridingMethods(psiMethod: PsiElement, scope: SearchScope): List<PsiElement>

    fun getJvmNamesForPropertyAccessors(element: PsiElement): Pair<String?, String?>

    /**
     * @return true if [element] is a light class for a regular Kotlin class (and not a facade class, for example).
     */
    fun isLightClassForRegularKotlinClass(element: KtLightClass): Boolean
}