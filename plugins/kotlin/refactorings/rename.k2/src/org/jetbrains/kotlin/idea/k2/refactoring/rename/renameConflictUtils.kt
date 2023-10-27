// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.conflicts.renderDescription
import org.jetbrains.kotlin.idea.refactoring.rename.BasicUnresolvableCollisionUsageInfo
import org.jetbrains.kotlin.idea.refactoring.rename.UsageInfoWithFqNameReplacement
import org.jetbrains.kotlin.idea.refactoring.rename.UsageInfoWithReplacement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtExpressionCodeFragment
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import kotlin.collections.mutableSetOf

fun checkClassNameShadowing(
    declaration: KtClassLikeDeclaration,
    newName: String,
    originalUsages: MutableList<UsageInfo>,
    newUsages: MutableList<UsageInfo>
) {

    fun KtType?.resolveFailed(): Boolean {
        return this == null || this is KtErrorType
    }

    val foreignReferences = mutableSetOf<Pair<PsiNamedElement, ClassId>>()
    val usageIterator = originalUsages.listIterator()
    while (usageIterator.hasNext()) {
        val usage = usageIterator.next()
        val refElement = usage.element as? KtSimpleNameExpression ?: continue
        val typeReference = refElement.getStrictParentOfType<KtTypeReference>() ?: continue

        fun createTypeFragment(type: String): KtExpressionCodeFragment {
            return KtPsiFactory(declaration.project).createExpressionCodeFragment("__foo__ as $type", typeReference)
        }

        val shortNameFragment = createTypeFragment(newName)
        val conflictingClassToClassIdPair = analyze(shortNameFragment) {
            val typeByShortName = shortNameFragment.getContentElement()?.getKtType()
            if (typeByShortName.resolveFailed()) {
                null
            } else {
                require(typeByShortName != null)
                val classSymbol = typeByShortName.expandedClassSymbol
                classSymbol?.psi as? PsiNamedElement to classSymbol?.classIdIfNonLocal
            }
        }

        if (conflictingClassToClassIdPair == null) {
            continue
        }

        val referencedClassFqName = analyze(typeReference) {
            val classSymbol = typeReference.getKtType().expandedClassSymbol
            classSymbol?.classIdIfNonLocal
        } ?: continue

        val (conflictingClass, conflictingClassId) = conflictingClassToClassIdPair
        if (conflictingClass != null && conflictingClassId != null) {
            foreignReferences.add(conflictingClass to conflictingClassId)
        }

        val newFqName = referencedClassFqName.asSingleFqName().parent().child(Name.identifier(newName))
        val codeFragment = createTypeFragment(newFqName.asString())
        analyze(codeFragment) {
            val ktType = codeFragment.getContentElement()?.getKtType()
            if (ktType.resolveFailed()) {
                usageIterator.set(UsageInfoWithFqNameReplacement(refElement, declaration, newFqName))
            } else {
                require(ktType != null)
                reportShadowing(declaration, declaration, ktType.expandedClassSymbol, refElement, newUsages)
            }
        }
    }

    foreignReferences.forEach { (klass, classId) ->
        val newFqName = classId.asSingleFqName()
        for (ref in ReferencesSearch.search(klass, declaration.useScope)) {
            val refElement = ref.element as? KtSimpleNameExpression ?: continue //todo cross language conflicts
            newUsages.add(UsageInfoWithFqNameReplacement(refElement, declaration, newFqName))
        }
    }
}

fun checkCallableShadowing(
    declaration: KtNamedDeclaration,
    newName: String,
    originalUsages: MutableList<UsageInfo>,
    newUsages: MutableList<UsageInfo>
) {
    val psiFactory = KtPsiFactory(declaration.project)
    val foreignDeclarationsToQualifyReferences = mutableSetOf<KtNamedDeclaration>()
    val usageIterator = originalUsages.listIterator()
    while (usageIterator.hasNext()) {

        val usage = usageIterator.next()
        val refElement = usage.element as? KtSimpleNameExpression ?: continue
        if (refElement.getStrictParentOfType<KtTypeReference>() != null) continue

        val callExpression = refElement.parent as? KtCallExpression ?: refElement.parent as? KtQualifiedExpression ?: refElement
        val copied = callExpression.copied()
        copied.referenceExpression().replace(psiFactory.createNameIdentifier(newName))
        val codeFragment = psiFactory.createExpressionCodeFragment(if (copied.isValid) copied.text else newName, callExpression)
        val contentElement = codeFragment.getContentElement()
        val referenceExpression = contentElement?.referenceExpression()

        if (referenceExpression != null) {
            analyze(codeFragment) {
                val newDeclaration = referenceExpression.mainReference?.resolve() as? KtNamedDeclaration
                if (newDeclaration != null && declaration is KtParameter && !declaration.hasValOrVar()) {
                    if (newDeclaration is KtProperty) {
                        foreignDeclarationsToQualifyReferences.add(newDeclaration)
                    }
                } else if (newDeclaration != null && !PsiTreeUtil.isAncestor(newDeclaration, declaration, true)) {
                    val fullCallExpression = callExpression.getQualifiedExpressionForSelectorOrThis()
                    val qualifiedExpression = createQualifiedExpression(callExpression, newName)
                    if (qualifiedExpression != null) {
                        usageIterator.set(UsageInfoWithReplacement(fullCallExpression, declaration, qualifiedExpression))
                    } else {
                        reportShadowing(declaration, declaration, newDeclaration.getSymbol(), refElement, newUsages)
                    }
                } else {
                    //k1 fails to compile otherwise
                }
            }
        }
    }

    val useScope = declaration.useScope
    for (foreignDeclaration in foreignDeclarationsToQualifyReferences) {
        ReferencesSearch.search(foreignDeclaration, useScope).forEach { ref ->
            val refElement = ref.element as? KtSimpleNameExpression ?: return@forEach
            val fullCallExpression = refElement
            val qualifiedExpression = createQualifiedExpression(refElement, newName)
            if (qualifiedExpression != null) {
                newUsages.add(UsageInfoWithReplacement(fullCallExpression, declaration, qualifiedExpression))
            }
        }
    }
}

private fun KtExpression.referenceExpression(): KtExpression {
    return (this as? KtCallExpression)?.calleeExpression ?: (this as? KtQualifiedExpression)?.selectorExpression ?: this
}

private fun createQualifiedExpression(callExpression: KtExpression, newName: String): KtExpression? {
    val psiFactory = KtPsiFactory(callExpression.project)
    val qualifiedExpression = analyze(callExpression) {
        val referenceExpression = callExpression.referenceExpression()
        val resolveCall = referenceExpression.resolveCall()
        val call = resolveCall?.successfulFunctionCallOrNull() ?: resolveCall?.successfulVariableAccessCall()
        val appliedSymbol = call?.partiallyAppliedSymbol
        val receiver = appliedSymbol?.dispatchReceiver ?: appliedSymbol?.extensionReceiver
        if (receiver is KtImplicitReceiverValue)  {
            val symbol = receiver.symbol
            if (symbol is KtClassifierSymbol && symbol !is KtAnonymousObjectSymbol) {
                "this@" + symbol.name!!.asString()
            } else {
                "this"
            }
        } else if (receiver == null) {
            (appliedSymbol?.symbol?.getContainingSymbol() as? KtClassOrObjectSymbol)?.classIdIfNonLocal?.asSingleFqName()?.parent()?.asString()
        }
        else null
    }?.let { psiFactory.createExpressionByPattern("$it.$0", callExpression) } ?: callExpression.copied()
    val newCallee = if (qualifiedExpression is KtCallableReferenceExpression) {
        qualifiedExpression.callableReference
    } else {
        qualifiedExpression.getQualifiedElementSelector() as? KtSimpleNameExpression
    }
    newCallee?.getReferencedNameElement()?.replace(psiFactory.createNameIdentifier(newName))
    return qualifiedExpression
}

context(KtAnalysisSession)
private fun reportShadowing(
    declaration: PsiNamedElement,
    elementToBindUsageInfoTo: PsiElement,
    candidateDescriptor: KtDeclarationSymbol?,
    refElement: PsiElement,
    result: MutableList<UsageInfo>
) {
    val candidate = candidateDescriptor?.psi as? PsiNamedElement ?: return
    if (declaration.parent == candidate.parent) return
    val message = KotlinBundle.message(
        "text.0.will.be.shadowed.by.1",
        declaration.renderDescription(),
        candidate.renderDescription()
    ).capitalize()
    result += BasicUnresolvableCollisionUsageInfo(refElement, elementToBindUsageInfoTo, message)
}