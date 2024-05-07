// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.searching.usages

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.util.Processor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.*
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KtRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KtDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.asJava.unwrapped
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

    context(KtAnalysisSession)
    private fun callReceiverRefersToCompanionObject(call: KtCall, companionObject: KtObjectDeclaration): Boolean {
        if (call !is KtCallableMemberCall<*, *>) return false
        val implicitReceivers = call.getImplicitReceivers()
        val companionObjectSymbol = companionObject.getSymbol()
        return companionObjectSymbol in implicitReceivers.map { it.symbol }
    }

    override fun tryRenderDeclarationCompactStyle(declaration: KtDeclaration): String {
        return KotlinPsiDeclarationRenderer.render(declaration) ?: analyzeInModalWindow(declaration, KotlinBundle.message(
          "find.usages.prepare.dialog.progress")) {
            declaration.getSymbol().render(noAnnotationsShortNameRenderer())
        }
    }

    private fun noAnnotationsShortNameRenderer(): KtDeclarationRenderer {
        return KtDeclarationRendererForSource.WITH_SHORT_NAMES.with {
            annotationRenderer = annotationRenderer.with {
                annotationFilter = KtRendererAnnotationsFilter.NONE
            }
        }
    }

    override fun formatJavaOrLightMethod(method: PsiMethod): String {
        val unwrapped = method.unwrapped as KtDeclaration
        return KotlinPsiDeclarationRenderer.render(unwrapped) ?: analyzeInModalWindow(unwrapped, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
            unwrapped.getSymbol().render(noAnnotationsShortNameRenderer())
        }
    }

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

        return withResolvedCall(psiToResolve) { call ->
            when (call) {
                is KtFunctionCall<*> -> {
                    val constructorSymbol = call.symbol as? KtConstructorSymbol ?: return@withResolvedCall false
                    val constructedClassSymbol =
                        constructorSymbol.getContainingSymbol() as? KtClassLikeSymbol ?: return@withResolvedCall false
                    val classOrObjectSymbol = ktClassOrObject.getClassOrObjectSymbol()

                    fun KtClassLikeSymbol.getExpectsOrSelf(): List<KtDeclarationSymbol> = (listOf(this).takeIf { isExpect } ?: getExpectsForActual())

                    constructedClassSymbol == classOrObjectSymbol ||
                            constructedClassSymbol.getExpectsOrSelf() == classOrObjectSymbol?.getExpectsOrSelf()
                }

                else -> false
            }
        } ?: false
    }

    override fun getSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>?): List<PsiElement> {
        if (!declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return emptyList()
        return analyzeInModalWindow(declaration, KotlinBundle.message("find.usages.progress.text.declaration.superMethods")) {
            (declaration.getSymbol() as? KtCallableSymbol)?.getAllOverriddenSymbols()?.mapNotNull { it.psi }?.toList().orEmpty()
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
        ).asSequence()

        is PsiClass -> ClassInheritorsSearch.search(element, searchScope, searchDeeply).asSequence()

        else -> emptySequence()
    }
}