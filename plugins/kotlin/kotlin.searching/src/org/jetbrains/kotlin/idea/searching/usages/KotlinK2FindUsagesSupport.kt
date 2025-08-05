// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.searching.usages

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.util.Processor
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.analysis.api.utils.getImplicitReceivers
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport
import org.jetbrains.kotlin.idea.references.KtInvokeFunctionReference
import org.jetbrains.kotlin.idea.searching.inheritors.DirectKotlinClassInheritorsSearch
import org.jetbrains.kotlin.idea.searching.inheritors.findAllInheritors
import org.jetbrains.kotlin.idea.searching.inheritors.findAllOverridings
import org.jetbrains.kotlin.idea.util.KotlinPsiDeclarationRenderer
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class KotlinK2FindUsagesSupport : KotlinFindUsagesSupport {
    override fun processCompanionObjectInternalReferences(
        companionObject: KtObjectDeclaration,
        referenceProcessor: Processor<PsiReference>
    ): Boolean {
        val klass = companionObject.getStrictParentOfType<KtClass>() ?: return true
        if (klass.containingKtFile.isCompiled) return true
        return !klass.anyDescendantOfType(fun(element: KtElement): Boolean {
            if (element == companionObject) return false
            return withResolvedCall(element) { call ->
                if (callReceiverRefersToCompanionObject(call, companionObject)) {
                    element.references.any {
                        // We get both a simple named reference and an invoke function
                        // reference for all function calls. We want the named reference.
                        //
                        // TODO: with FE1.0 the check for reference type is not needed.
                        // With FE1.0 two references that point to the same PSI are
                        // obtained and one is filtered out by the reference processor.
                        // We should make FIR references behave the same.
                        it !is KtInvokeFunctionReference && !referenceProcessor.process(it)
                    }
                } else {
                    false
                }
            } ?: false
        })
    }

    context(_: KaSession)
    private fun callReceiverRefersToCompanionObject(call: KaCall, companionObject: KtObjectDeclaration): Boolean {
        if (call !is KaCallableMemberCall<*, *>) return false
        val implicitReceivers = call.getImplicitReceivers()
        val companionObjectSymbol = companionObject.symbol
        return companionObjectSymbol in implicitReceivers.map { it.symbol }
    }

    @OptIn(KaExperimentalApi::class)
    override fun tryRenderDeclarationCompactStyle(declaration: KtDeclaration): String {
        return KotlinPsiDeclarationRenderer.render(declaration) ?: analyzeInModalWindow(declaration, KotlinBundle.message(
          "find.usages.prepare.dialog.progress")) {
            declaration.symbol.render(noAnnotationsShortNameRenderer())
        }
    }

    @KaExperimentalApi
    private fun noAnnotationsShortNameRenderer(): KaDeclarationRenderer {
        return KaDeclarationRendererForSource.WITH_SHORT_NAMES.with {
            annotationRenderer = annotationRenderer.with {
                annotationFilter = KaRendererAnnotationsFilter.NONE
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    override fun renderDeclaration(method: KtDeclaration): String {
        return KotlinPsiDeclarationRenderer.render(method) ?: analyzeInModalWindow(method, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
            method.symbol.render(noAnnotationsShortNameRenderer())
        }
    }

    @OptIn(KaExperimentalApi::class)
    override fun isKotlinConstructorUsage(psiReference: PsiReference, ktClassOrObject: KtClassOrObject): Boolean {
        val element = psiReference.element
        if (element !is KtElement) return false

        fun adaptSuperCall(psi: KtElement): KtElement? {
            if (psi !is KtNameReferenceExpression) return null
            val userType = psi.parent as? KtUserType ?: return null
            val typeReference = userType.parent as? KtTypeReference ?: return null
            return typeReference.parent as? KtConstructorCalleeExpression
        }

        val psiToResolve = adaptSuperCall(element) ?: element
        return analyze(psiToResolve) {
            // The element cannot refer classes from unrelated sessions
            if (!ktClassOrObject.canBeAnalysed()) return false

            withResolvedCall(psiToResolve) { call ->
                when (call) {
                    is KaFunctionCall<*> -> {
                        val constructorSymbol = call.symbol as? KaConstructorSymbol ?: return@withResolvedCall false
                        val constructedClassSymbol =
                            constructorSymbol.containingDeclaration as? KaClassLikeSymbol ?: return@withResolvedCall false
                        val classOrObjectSymbol = ktClassOrObject.classSymbol

                        fun KaClassLikeSymbol.getExpectsOrSelf(): List<KaDeclarationSymbol> = (listOf(this).takeIf { isExpect } ?: getExpectsForActual())

                        constructedClassSymbol == classOrObjectSymbol ||
                                constructedClassSymbol.getExpectsOrSelf() == classOrObjectSymbol?.getExpectsOrSelf()
                    }

                    else -> false
                }
            } == true
        }
    }

    override fun getSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>?): List<PsiElement> {
        if (!declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return emptyList()
        return analyzeInModalWindow(declaration, KotlinBundle.message("find.usages.progress.text.declaration.superMethods")) {
            (declaration.symbol as? KaCallableSymbol)?.allOverriddenSymbols?.mapNotNull { it.psi }?.toList().orEmpty()
        }
    }

    override fun searchOverriders(
        element: PsiElement,
        searchScope: SearchScope,
    ): Sequence<PsiElement> = (element as? KtCallableDeclaration)?.findAllOverridings(searchScope) ?: emptySequence()

    override fun searchInheritors(
        element: PsiElement,
        searchScope: SearchScope,
        searchDeeply: Boolean,
    ): Sequence<PsiElement> = when (element) {
        is KtClass -> if (searchDeeply) element.findAllInheritors(searchScope) else DirectKotlinClassInheritorsSearch.search(
            element, searchScope
        ).asIterable().asSequence()

        is PsiClass -> ClassInheritorsSearch.search(element, searchScope, searchDeeply).asIterable().asSequence()

        else -> emptySequence()
    }
}