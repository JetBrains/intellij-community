// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinRenameRefactoringSupport
import org.jetbrains.kotlin.idea.searching.inheritors.findAllOverridings
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.NameUtils
import org.jetbrains.kotlin.psi.*

internal class K2RenameRefactoringSupport : KotlinRenameRefactoringSupport {
    private fun notImplementedInK2(): Nothing {
        throw IncorrectOperationException()
    }

    override fun processForeignUsages(
        element: PsiElement,
        newName: String,
        usages: Array<UsageInfo>,
        fallbackHandler: (UsageInfo) -> Unit
    ) {
        usages.forEach(fallbackHandler)
    }

    override fun prepareForeignUsagesRenaming(
        element: PsiElement,
        newName: String,
        allRenames: MutableMap<PsiElement, String>,
        scope: SearchScope
    ) {}

    override fun checkRedeclarations(declaration: KtNamedDeclaration, newName: String, result: MutableList<UsageInfo>) {
        // TODO
    }

    override fun checkOriginalUsagesRetargeting(
        declaration: KtNamedDeclaration,
        newName: String,
        originalUsages: MutableList<UsageInfo>,
        newUsages: MutableList<UsageInfo>
    ) {
        // TODO
    }

    override fun checkNewNameUsagesRetargeting(declaration: KtNamedDeclaration, newName: String, newUsages: MutableList<UsageInfo>) {
        // TODO
    }

    override fun checkAccidentalPropertyOverrides(declaration: KtNamedDeclaration, newName: String, result: MutableList<UsageInfo>) {
        // TODO
    }

    override fun getAllOverridenFunctions(function: KtNamedFunction): List<PsiElement> {
        return analyze(function) {
            val overridenFunctions = (function.getSymbol() as? KtCallableSymbol)?.getAllOverriddenSymbols().orEmpty()
            overridenFunctions.mapNotNull { it.psi as? KtNamedFunction }
        }
    }

    override fun getModuleNameSuffixForMangledName(mangledName: String): String? {
        notImplementedInK2()
    }

    override fun mangleInternalName(name: String, moduleName: String): String {
        return name + "$" + NameUtils.sanitizeAsJavaIdentifier(moduleName)
    }

    override fun demangleInternalName(mangledName: String): String? {
        val indexOfDollar = mangledName.indexOf('$')
        return if (indexOfDollar >= 0) mangledName.substring(0, indexOfDollar) else null
    }

    override fun actualsForExpected(declaration: KtDeclaration): Set<KtDeclaration> {
        notImplementedInK2()
    }

    override fun liftToExpected(declaration: KtDeclaration): KtDeclaration? {
        return null
    }

    override fun getJvmName(element: PsiElement): String? {
        return null
    }

    override fun isCompanionObjectClassReference(psiReference: PsiReference): Boolean {
        notImplementedInK2()
    }

    override fun shortenReferencesLater(element: KtElement) {
        notImplementedInK2()
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun dropOverrideKeywordIfNecessary(element: KtNamedDeclaration) {
        fun KtCallableDeclaration.overridesNothing(): Boolean {
            val declaration = this

            analyze(this) {
                val declarationSymbol = declaration.getSymbol() as? KtCallableSymbol ?: return false

                return declarationSymbol.getDirectlyOverriddenSymbols().isEmpty()
            }
        }

        fun dropOverrideKeywordIfNecessary(declaration: KtCallableDeclaration) {
            if (declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD) && declaration.overridesNothing()) {
                declaration.removeModifier(KtTokens.OVERRIDE_KEYWORD)
            }
        }

        allowAnalysisOnEdt {
            dropOverrideKeywordIfNecessary(element as? KtCallableDeclaration ?: return)
        }

    }

    override fun findAllOverridingMethods(psiMethod: PsiMethod, scope: SearchScope): List<PsiMethod> {
        return when (val element = psiMethod.unwrapped) {
            is PsiMethod -> notImplementedInK2()

            is KtCallableDeclaration -> {
                val allOverrides = element.findAllOverridings(scope).toList()

                val lightOverrides = allOverrides
                    .flatMap { runReadAction { it.toLightMethods() } }
                    .distinctBy { it.unwrapped }

                lightOverrides
            }

            else -> error("Unexpected class ${psiMethod::class}")
        }
    }

    override fun getJvmNamesForPropertyAccessors(element: PsiElement): Pair<String?, String?> {
        return null to null
    }

    override fun isLightClassForRegularKotlinClass(element: KtLightClass): Boolean {
        // FIXME make the comparison more robust
        return element.kotlinOrigin?.name == element.name
    }
}