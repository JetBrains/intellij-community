// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaKotlinPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.analysis.api.utils.getJvmName
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinRenameRefactoringSupport
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.searching.inheritors.findAllOverridings
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.NameUtils
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal class K2RenameRefactoringSupport : KotlinRenameRefactoringSupport {

    override fun checkUsagesRetargeting(
        declaration: KtNamedDeclaration,
        newName: String,
        originalUsages: MutableList<UsageInfo>,
        newUsages: MutableList<UsageInfo>
    ) {
        if (declaration is KtClassLikeDeclaration) {
            checkClassNameShadowing(declaration, newName.quoteIfNeeded(), originalUsages, newUsages)
        } else if (declaration is KtTypeParameter) {
            //it's impossible to fix type parameter usages, let's try to fix external usages
            checkClassLikeNameShadowing(declaration, newName.quoteIfNeeded(), newUsages)
        }
        checkCallableShadowing(declaration, newName.quoteIfNeeded(), originalUsages, newUsages)
    }

    override fun getAllOverridenFunctions(function: KtNamedFunction): List<PsiElement> {
        return analyze(function) {
            val overridenFunctions = (function.symbol as? KaCallableSymbol)?.allOverriddenSymbols?.toList().orEmpty()
            overridenFunctions.mapNotNull { it.psi as? KtNamedFunction }
        }
    }

    override fun mangleInternalName(name: String, moduleName: String): String {
        return name + "$" + NameUtils.sanitizeAsJavaIdentifier(moduleName)
    }

    override fun demangleInternalName(mangledName: String): String? {
        val indexOfDollar = mangledName.indexOf('$')
        return if (indexOfDollar >= 0) mangledName.substring(0, indexOfDollar) else null
    }

    override fun getJvmName(element: PsiElement): String? {
        val property = element.unwrapped as? KtDeclaration ?: return null
        analyseOnEdt(property) {
            val propertySymbol = property.symbol as? KaCallableSymbol
            return propertySymbol?.let { getJvmName(it) }
        }
    }

    override fun isCompanionObjectClassReference(psiReference: PsiReference): Boolean {
        if (psiReference !is KtSimpleNameReference) {
            return false
        }
        return analyze(psiReference.element) {
            psiReference.isImplicitReferenceToCompanion()
        }
    }

    override fun shortenReferencesLater(element: KtElement) {
        shortenReferences(element)
    }

    override fun dropOverrideKeywordIfNecessary(element: KtNamedDeclaration) {
        val declaration = element as? KtCallableDeclaration ?: return
        if (overridesNothing(declaration)) {
            declaration.removeModifier(KtTokens.OVERRIDE_KEYWORD)
        }
    }

    override fun findAllOverridingMethods(psiMethod: PsiElement, scope: SearchScope): List<PsiElement> {
        return when (val element = psiMethod.unwrapped) {
            is PsiMethod -> OverridingMethodsSearch.search(element, scope, /* checkDeep = */ true).toList()

            is KtCallableDeclaration -> {
                element.findAllOverridings(scope).toList()
            }

            else -> error("Unexpected class ${psiMethod::class}")
        }
    }

    override fun getJvmNamesForPropertyAccessors(element: PsiElement): Pair<String?, String?> {
        val propertyOrParameter = element.unwrapped as? KtDeclaration ?: return null to null

        analyseOnEdt(propertyOrParameter) {
            val propertySymbol = when (val symbol = propertyOrParameter.symbol) {
                is KaKotlinPropertySymbol -> symbol
                is KaValueParameterSymbol -> symbol.generatedPrimaryConstructorProperty
                else -> null
            }

            val getter = propertySymbol?.getter
            val setter = propertySymbol?.setter

            return getter?.let { getJvmName(it) } to setter?.let { getJvmName(it) }
        }
    }

    override fun isLightClassForRegularKotlinClass(element: KtLightClass): Boolean {
        // FIXME make the comparison more robust
        return element.kotlinOrigin?.name == element.name
    }

    /**
     * Rename calls a lot of resolve on EDT, so occasionally it's easier to allow resolve
     * on EDT than to move the whole operation to a background thread.
     *
     * Please, do not try to move this function to some util module. Usage of
     * [allowAnalysisOnEdt] should generally be avoided.
     */
    @OptIn(KaAllowAnalysisOnEdt::class, ExperimentalContracts::class)
    private inline fun <T> analyseOnEdt(element: KtElement, action: KaSession.() -> T) {
        contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }

        return allowAnalysisOnEdt { analyze(element, action = action) }
    }
}