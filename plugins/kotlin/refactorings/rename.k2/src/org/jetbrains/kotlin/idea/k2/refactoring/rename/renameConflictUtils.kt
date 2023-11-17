// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.KtExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.KtImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.scopes.KtScope
import org.jetbrains.kotlin.analysis.api.symbols.KtAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.conflicts.findSiblingsByName
import org.jetbrains.kotlin.idea.refactoring.conflicts.renderDescription
import org.jetbrains.kotlin.idea.refactoring.rename.BasicUnresolvableCollisionUsageInfo
import org.jetbrains.kotlin.idea.refactoring.rename.UsageInfoWithFqNameReplacement
import org.jetbrains.kotlin.idea.refactoring.rename.UsageInfoWithReplacement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtExpressionCodeFragment
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import kotlin.collections.mutableSetOf

fun checkClassNameShadowing(
    declaration: KtClassLikeDeclaration,
    newName: String,
    originalUsages: MutableList<UsageInfo>,
    newUsages: MutableList<UsageInfo>
) {

    val newFqName = declaration.fqName?.parent()?.let { it.child(Name.identifier(newName)) }

    if (newFqName != null) {
        val usageIterator = originalUsages.listIterator()
        while (usageIterator.hasNext()) {
            val usage = usageIterator.next()
            val refElement = usage.element as? KtSimpleNameExpression ?: continue
            val typeReference = refElement.getStrictParentOfType<KtTypeReference>() ?: continue

            fun createTypeFragment(type: String): KtExpressionCodeFragment {
                return KtPsiFactory(declaration.project).createExpressionCodeFragment("__foo__ as $type", typeReference)
            }

            val shortNameFragment = createTypeFragment(newName)
            val hasConflict = analyze(shortNameFragment) {
                val typeByShortName = shortNameFragment.getContentElement()?.getKtType()
                typeByShortName != null && typeByShortName !is KtErrorType
            }

            if (hasConflict) {
                usageIterator.set(UsageInfoWithFqNameReplacement(refElement, declaration, newFqName))
            }
        }
    }

    analyze(declaration) {
        //check outer classes hiding/hidden by rename
        val processedClasses = mutableSetOf<KtClassOrObject>()
        retargetExternalDeclarations(declaration, newName) {
            val klass = it.psi as? KtClassOrObject
            val newFqName = klass?.fqName
            if (newFqName != null && processedClasses.add(klass)) {
                for (ref in ReferencesSearch.search(klass, declaration.useScope)) {
                    val refElement = ref.element as? KtSimpleNameExpression ?: continue //todo cross language conflicts
                    newUsages.add(UsageInfoWithFqNameReplacement(refElement, declaration, newFqName))
                }
            }
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
    val externalProperties = mutableSetOf<KtCallableDeclaration>()
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
                if ((newDeclaration is KtProperty || newDeclaration is KtParameter && newDeclaration.hasValOrVar()) && declaration is KtParameter) {
                    externalProperties.add(newDeclaration as KtCallableDeclaration)
                }
                if (newDeclaration != null && (declaration !is KtParameter || declaration.hasValOrVar()) && !PsiTreeUtil.isAncestor(newDeclaration, declaration, true)) {
                    val qualifiedExpression = createQualifiedExpression(refElement, newName)
                    if (qualifiedExpression != null) {
                        usageIterator.set(UsageInfoWithReplacement(refElement, declaration, qualifiedExpression))
                    } else {
                        reportShadowing(declaration, declaration, newDeclaration.getSymbol(), refElement, newUsages)
                    }
                } else {
                    //k1 fails to compile otherwise
                }
            }
        }
    }

    fun retargetExternalDeclaration(externalDeclaration: KtCallableDeclaration) {
        ReferencesSearch.search(externalDeclaration, declaration.useScope).forEach { ref ->
            val refElement = ref.element as? KtSimpleNameExpression ?: return@forEach
            val fullCallExpression = refElement
            val qualifiedExpression = createQualifiedExpression(refElement, newName)
            if (qualifiedExpression != null) {
                newUsages.add(UsageInfoWithReplacement(fullCallExpression, declaration, qualifiedExpression))
            }
        }
    }

    for (externalDeclaration in externalProperties) {
        retargetExternalDeclaration(externalDeclaration)
    }

    analyze(declaration) {
        //check outer callables hiding/hidden by rename
        val processedCallables = mutableSetOf<KtCallableDeclaration>()
        retargetExternalDeclarations(declaration, newName) {
            val callableDeclaration = it.psi as? KtCallableDeclaration
            if (callableDeclaration != null && processedCallables.add(callableDeclaration)) {
                retargetExternalDeclaration(callableDeclaration)
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
        val appliedSymbol = resolveCall?.successfulCallOrNull<KtCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
        val receiver = appliedSymbol?.dispatchReceiver ?: appliedSymbol?.extensionReceiver
        if (receiver is KtImplicitReceiverValue) {
            val symbol = receiver.symbol
            if ((symbol as? KtClassOrObjectSymbol)?.classKind == KtClassKind.COMPANION_OBJECT) {
                //specify companion name to avoid clashes with enum entries
                symbol.name!!.asString()
            }
            else if (symbol is KtClassifierSymbol && symbol !is KtAnonymousObjectSymbol) {
                "this@" + symbol.name!!.asString()
            }
            else if (symbol is KtReceiverParameterSymbol && symbol.owningCallableSymbol is KtNamedSymbol) {
                receiver.type.expandedClassSymbol?.name?.let { "this@$it" } ?: "this"
            }
            else {
                "this"
            }
        } else if (receiver == null) {
            val symbol = appliedSymbol?.symbol
            val containingSymbol = symbol?.getContainingSymbol()
            val containerFQN =
                if (containingSymbol is KtClassOrObjectSymbol) {
                    containingSymbol.classIdIfNonLocal?.asSingleFqName()?.parent()
                } else {
                    (symbol?.psi as? KtElement)?.containingKtFile?.packageFqName
                }
            containerFQN?.asString()?.takeIf { it.isNotEmpty() }
        }
        else if (receiver is KtExplicitReceiverValue) {
            val containingSymbol = appliedSymbol?.symbol?.getContainingSymbol()
            val enumClassSymbol = containingSymbol?.getContainingSymbol()
            //add companion qualifier to avoid clashes with enum entries
            if (containingSymbol is KtNamedClassOrObjectSymbol && containingSymbol.classKind == KtClassKind.COMPANION_OBJECT &&
                enumClassSymbol is KtNamedClassOrObjectSymbol && enumClassSymbol.classKind == KtClassKind.ENUM_CLASS &&
                (receiver.expression as? KtNameReferenceExpression)?.mainReference?.resolve() == containingSymbol.psi
            ) {
                containingSymbol.name.asString()
            } else null
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

context(KtAnalysisSession)
private fun retargetExternalDeclarations(declaration: KtNamedDeclaration, name: String, retargetJob: (KtDeclarationSymbol) -> Unit) {
    val declarationSymbol = declaration.getSymbol()

    val nameAsName = Name.identifier(name)
    fun KtScope.processScope(containingSymbol: KtDeclarationSymbol?) {
        findSiblingsByName(declarationSymbol, nameAsName, containingSymbol).forEach(retargetJob)
    }

    var classOrObjectSymbol = declarationSymbol.getContainingSymbol()
    while (classOrObjectSymbol != null) {
        (classOrObjectSymbol as? KtClassOrObjectSymbol)?.getMemberScope()?.processScope(classOrObjectSymbol)

        val companionObject = (classOrObjectSymbol as? KtNamedClassOrObjectSymbol)?.companionObject
        companionObject?.getMemberScope()?.processScope(companionObject)

        classOrObjectSymbol = classOrObjectSymbol.getContainingSymbol()
    }

    val file = declaration.containingKtFile
    getPackageSymbolIfPackageExists(file.packageFqName)?.getPackageScope()?.processScope(null)
    file.getImportingScopeContext().getCompositeScope().processScope(null)
}