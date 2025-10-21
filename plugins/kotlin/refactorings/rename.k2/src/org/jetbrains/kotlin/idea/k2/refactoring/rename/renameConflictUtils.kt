// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.psi.*
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.k2.refactoring.getThisQualifier
import org.jetbrains.kotlin.idea.refactoring.conflicts.filterCandidates
import org.jetbrains.kotlin.idea.refactoring.conflicts.registerRetargetJobOnPotentialCandidates
import org.jetbrains.kotlin.idea.refactoring.conflicts.renderDescription
import org.jetbrains.kotlin.idea.refactoring.rename.BasicUnresolvableCollisionUsageInfo
import org.jetbrains.kotlin.idea.refactoring.rename.UsageInfoWithFqNameReplacement
import org.jetbrains.kotlin.idea.refactoring.rename.UsageInfoWithReplacement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.utils.addIfNotNull

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
                val typeByShortName = shortNameFragment.getContentElement()?.expressionType
                typeByShortName != null && typeByShortName !is KaErrorType
            }

            if (hasConflict) {
                usageIterator.set(UsageInfoWithFqNameReplacement(refElement, declaration, newFqName))
            }
        }
    }

    checkClassLikeNameShadowing(declaration, newName, newUsages)
}

fun checkClassLikeNameShadowing(declaration: KtNamedDeclaration, newName: String, newUsages: MutableList<UsageInfo>) {
    analyze(declaration) {
        //check outer classes hiding/hidden by rename
        val processedClasses = mutableSetOf<PsiElement>()
        retargetExternalDeclarations(declaration, newName) {
            val klass = it.psi
            val newFqName = (klass as? KtClassOrObject)?.fqName ?: (klass as? PsiClass)?.qualifiedName?.let { FqName.fromSegments(it.split(".")) }
            if (newFqName != null && klass != null && processedClasses.add(klass)) {
                for (ref in ReferencesSearch.search(klass, declaration.useScope).asIterable()) {
                    val refElement = ref.element as? KtSimpleNameExpression ?: continue //todo cross language conflicts
                    if (refElement.getStrictParentOfType<KtTypeReference>() != null) {
                        //constructor (also implicit) calls would be processed together with other callables
                        newUsages.add(UsageInfoWithFqNameReplacement(refElement, declaration, newFqName))
                    }
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
    val shadowedElements = newUsages.mapNotNull { (it as? BasicUnresolvableCollisionUsageInfo)?.element }.toSet()

    val psiFactory = KtPsiFactory(declaration.project)
    val externalDeclarations = mutableSetOf<PsiElement>()
    val notQualifiedExternalDeclarations = mutableMapOf<PsiElement, PsiElement>()
    val usageIterator = originalUsages.listIterator()
    while (usageIterator.hasNext()) {

        val usage = usageIterator.next()
        val refElement = usage.element as? KtSimpleNameExpression ?: continue
        if (refElement.getStrictParentOfType<KtTypeReference>() != null ||
            refElement.getStrictParentOfType<KtImportDirective>() != null ||
            refElement.parent is KtValueArgumentName) continue

        val parent = refElement.parent
        val callExpression = parent as? KtCallExpression ?: parent as? KtQualifiedExpression ?: parent as? KtCallableReferenceExpression ?: refElement
        val topLevel = PsiTreeUtil.getTopmostParentOfType(refElement, KtQualifiedExpression::class.java) ?: callExpression
        val offsetInCopy = callExpression.textRange.shiftLeft(topLevel.textRange.startOffset)

        //take top level qualified expression, perform copy and replace initial refElement in copy with a newName
        val codeFragment = psiFactory.createCodeFragmentWithNewName(callExpression, topLevel, newName)

        val newDeclaration = analyze(codeFragment) {
            //restore callExpression in copy
            //offsets are required because context is ignored in KtPsiFactory and codeFragment is always created with eventsEnabled on,
            //meaning that you can't change it without WA which is here not allowed, because conflict checking is under RA in progress
            val copyCallExpression =
                CodeInsightUtilCore.findElementInRange(codeFragment.containingFile,
                                                       offsetInCopy.startOffset,
                                                       offsetInCopy.endOffset + newName.length - declaration.nameAsSafeName.asString().length,
                                                       callExpression.javaClass,
                                                       KotlinLanguage.INSTANCE)

            val resolveCall = copyCallExpression?.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()
            val resolvedSymbol = resolveCall?.partiallyAppliedSymbol?.symbol
            if (resolvedSymbol is KaSyntheticJavaPropertySymbol) {
                val getter = resolvedSymbol.javaGetterSymbol.psi
                externalDeclarations.addIfNotNull(getter)
                externalDeclarations.addIfNotNull(resolvedSymbol.javaSetterSymbol?.psi)
                getter
            } else {
                val element = resolvedSymbol?.psi
                    //callable references are ignored now by resolveCallOld() in AA, thus they require separate treatment here
                    ?: (copyCallExpression as? KtCallableReferenceExpression)?.callableReference?.mainReference?.resolve()
                externalDeclarations.addIfNotNull(element)
                element
            }
        }

        if (newDeclaration != null && (declaration !is KtParameter || declaration.hasValOrVar()) && !PsiTreeUtil.isAncestor(newDeclaration, declaration, true)) {
            if (newDeclaration in shadowedElements) continue
            val expression = refElement.parent as? KtCallExpression ?: refElement
            val qualifiedState = createQualifiedExpression(expression, newName)
            if (qualifiedState != null) {
                val qualifiedExpression = qualifiedState.expression
                if (qualifiedExpression != null) {
                    val newReferenceExpression = //can't call resolve on qualified expression because it doesn't have context
                        getNewCallee(psiFactory.createExpressionCodeFragment(qualifiedExpression.text, callExpression).getContentElement())
                    if (newReferenceExpression?.mainReference?.resolve() == newDeclaration) {
                        //if we tried to qualify (no explicit receiver) but it still points to external declaration,
                        //means that most probably it's impossible to distinguish calls
                        reportShadowing(declaration, newDeclaration, refElement, newUsages)
                    }
                    else {
                        usageIterator.set(UsageInfoWithReplacement(expression, declaration, qualifiedExpression))
                    }

                    continue
                }
            }

            if (qualifiedState == null || !qualifiedState.explicitlyQualified) {
                //if we could not qualify, e.g. anonymous or local,
                //probably we can qualify all references to external declaration and that's fine
                notQualifiedExternalDeclarations[newDeclaration] = refElement
            }
        }
    }

    fun retargetExternalDeclaration(externalDeclaration: PsiElement) {
        val needToBeQualified = notQualifiedExternalDeclarations.contains(externalDeclaration)
        val processor: (PsiReference) -> Boolean = processor@ { ref ->
            val refElement = ref.element as? KtSimpleNameExpression ?: return@processor true
            if (refElement.getStrictParentOfType<KtTypeReference>() != null ||
                refElement.getStrictParentOfType<KtImportDirective>() != null) {
                return@processor true
            }

            val expression = refElement.parent as? KtCallExpression ?: refElement
            val qualifiedState = createQualifiedExpression(expression, newName)
            if (qualifiedState?.expression != null) {
                newUsages.add(UsageInfoWithReplacement(expression, declaration, qualifiedState.expression))
                return@processor true
            }
            return@processor !needToBeQualified || qualifiedState?.explicitlyQualified == true
        }
        val success = if (externalDeclaration is PsiMethod) {
            MethodReferencesSearch.search(externalDeclaration, declaration.useScope, true).forEach(processor)
        }
        else {
            ReferencesSearch.search(externalDeclaration, declaration.useScope).forEach(processor)
        }

        if (!success && needToBeQualified) {
            reportShadowing(declaration, externalDeclaration, notQualifiedExternalDeclarations[externalDeclaration]!!, newUsages)
        }
    }

    for (externalDeclaration in externalDeclarations) {
        if (externalDeclaration !in shadowedElements) {
            retargetExternalDeclaration(externalDeclaration)
        }
    }

    analyze(declaration) {
        //check outer callables hiding/hidden by rename
        val processedDeclarations = mutableSetOf<PsiElement>()
        processedDeclarations.addAll(externalDeclarations)
        processedDeclarations.addAll(shadowedElements)
        retargetExternalDeclarations(declaration, newName) {
            val callableDeclaration = it.psi as? KtNamedDeclaration
            if (callableDeclaration != null && processedDeclarations.add(callableDeclaration)) {
                retargetExternalDeclaration(callableDeclaration)
            }
        }
    }
}

private fun getNewCallee(ktExpression: KtExpression?): KtSimpleNameExpression? {
    return if (ktExpression is KtCallableReferenceExpression) {
        ktExpression.callableReference
    } else {
        ktExpression?.getQualifiedElementSelector() as? KtSimpleNameExpression
    }
}

private fun KtPsiFactory.createCodeFragmentWithNewName(
    callExpression: KtExpression,
    topLevel: KtExpression,
    newName: String
): KtExpressionCodeFragment {
    val mark = PsiTreeUtil.mark(callExpression)
    val topLevelCopy = topLevel.copy()
    val copiedExpression = PsiTreeUtil.releaseMark(topLevelCopy, mark) as KtExpression
    val replacement = createExpression(newName)
    val replaced = when (copiedExpression) {
        is KtCallExpression -> {
            copiedExpression.calleeExpression?.replace(replacement)
            topLevelCopy
        }

        is KtQualifiedExpression -> {
            copiedExpression.selectorExpression?.replace(replacement)
            topLevelCopy
        }

        is KtCallableReferenceExpression -> {
            copiedExpression.callableReference.replace(replacement)
            topLevelCopy
        }

        else -> {
            val sameTopLevel = topLevel == callExpression
            val expression = copiedExpression.replace(replacement) as KtExpression
            if (sameTopLevel) expression else topLevelCopy
        }
    }
    return createExpressionCodeFragment(replaced.text, callExpression)
}

private data class QualifiedState(val expression: KtExpression?, val explicitlyQualified: Boolean)

private fun createQualifiedExpression(callExpression: KtExpression, newName: String): QualifiedState? {
    val psiFactory = KtPsiFactory(callExpression.project)
    analyze(callExpression) {
        val appliedSymbol = callExpression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
        val receiver = appliedSymbol?.extensionReceiver ?: appliedSymbol?.dispatchReceiver

        fun getExplicitQualifier(receiverValue: KaExplicitReceiverValue): String? {
            val containingSymbol = appliedSymbol?.symbol?.containingDeclaration
            val enumClassSymbol = containingSymbol?.containingDeclaration
            //add companion qualifier to avoid clashes with enum entries
            return if (containingSymbol is KaNamedClassSymbol && containingSymbol.classKind == KaClassKind.COMPANION_OBJECT &&
                enumClassSymbol is KaNamedClassSymbol && enumClassSymbol.classKind == KaClassKind.ENUM_CLASS &&
                (receiverValue.expression as? KtNameReferenceExpression)?.mainReference?.resolve() == containingSymbol.psi
            ) {
                containingSymbol.name.asString()
            } else {
                null
            }
        }

        val qualifierText = when (receiver) {
            is KaImplicitReceiverValue -> getThisQualifier(receiver)

            is KaExplicitReceiverValue -> {
                getExplicitQualifier(receiver) ?: return QualifiedState(null, true)
            }

            is KaSmartCastedReceiverValue -> {
                when (val receiverValue = receiver.original) {
                    is KaImplicitReceiverValue -> getThisQualifier(receiverValue)
                    is KaExplicitReceiverValue -> getExplicitQualifier(receiverValue)
                    else -> null
                }
            }

            null -> {
                val symbol = appliedSymbol?.symbol
                val containingSymbol = symbol?.containingDeclaration
                val containerFQN =
                    if (containingSymbol is KaClassSymbol) {
                        containingSymbol.classId?.asSingleFqName()?.parent()
                    } else if (containingSymbol == null) {
                        (symbol?.psi as? KtElement)?.containingKtFile?.packageFqName
                    } else null
                containerFQN?.quoteIfNeeded()?.asString()?.takeIf { it.isNotEmpty() }
            }
        } ?: return null

        val qualifiedExpression = psiFactory.createExpressionByPattern("$qualifierText.$0", callExpression)
        getNewCallee(qualifiedExpression)?.getReferencedNameElement()?.replace(psiFactory.createNameIdentifier(newName))
        return QualifiedState(qualifiedExpression, receiver is KaExplicitReceiverValue)
    }
}

private fun reportShadowing(
    declaration: PsiNamedElement,
    candidate: PsiElement,
    refElement: PsiElement,
    result: MutableList<UsageInfo>
) {
    val candidatePresentation = (candidate as? PsiNamedElement)?.renderDescription() ?: return
    val message = KotlinBundle.message(
        "text.0.will.be.shadowed.by.1",
        declaration.renderDescription(),
        candidatePresentation
    ).capitalize()
    result += BasicUnresolvableCollisionUsageInfo(refElement, declaration, message)
}

context(_: KaSession)
private fun retargetExternalDeclarations(declaration: KtNamedDeclaration, name: String, retargetJob: (KaDeclarationSymbol) -> Unit) {
    val declarationSymbol = declaration.symbol
    registerRetargetJobOnPotentialCandidates(declaration, name, { filterCandidates(declarationSymbol, it) }, retargetJob)
}