// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pushDown

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.findCallableMemberBySignature
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.renderForConflicts
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KtPsiClassWrapper
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny

internal fun KaSession.analyzePushDownConflicts(
    context: K2PushDownContext,
    usages: Array<out UsageInfo>
): MultiMap<PsiElement, String> {
    val targetClasses = usages.mapNotNull { it.element?.unwrapped }

    val conflicts = MultiMap<PsiElement, String>()

    val membersToPush = ArrayList<KtNamedDeclaration>()
    val membersToKeepAbstract = ArrayList<KtNamedDeclaration>()
    for (info in context.membersToMove) {
        val member = info.member
        if (!info.isChecked || ((member is KtClassOrObject || member is KtPsiClassWrapper) && info.overrides != null)) continue

        membersToPush += member
        if ((member is KtNamedFunction || member is KtProperty) && info.isToAbstract && (member.symbol as? KaCallableSymbol)?.modality != KaSymbolModality.ABSTRACT) {
            membersToKeepAbstract += member
        }
    }

    for (targetClass in targetClasses) {
        checkConflicts(conflicts, context, targetClass, membersToKeepAbstract, membersToPush)
    }

    return conflicts
}

@OptIn(KaExperimentalApi::class)
internal fun KaSession.checkConflicts(
    conflicts: MultiMap<PsiElement, String>,
    context: K2PushDownContext,
    targetClass: PsiElement,
    membersToKeepAbstract: List<KtNamedDeclaration>,
    membersToPush: ArrayList<KtNamedDeclaration>
) {
    if (targetClass !is KtClassOrObject) {
        conflicts.putValue(
            targetClass,
            KotlinBundle.message(
                "text.non.kotlin.0.will.not.be.affected.by.refactoring",
                RefactoringUIUtil.getDescription(targetClass, false)
            )
        )
        return
    }

    val targetClassSymbol = targetClass.symbol as KaClassSymbol
    val sourceClass = context.sourceClass

    val substitutor = createInheritanceTypeSubstitutor(
        subClass = targetClassSymbol,
        superClass = sourceClass.symbol as KaClassSymbol,
    ) ?: KaSubstitutor.Empty(token)

    if (!context.sourceClass.isInterface() && targetClass is KtClass && targetClass.isInterface()) {
        val message = KotlinBundle.message(
            "text.0.inherits.from.1.it.will.not.be.affected.by.refactoring",
            targetClassSymbol.renderForConflicts(analysisSession = this),
            context.sourceClass.symbol.renderForConflicts(analysisSession = this),
        )
        conflicts.putValue(targetClass, message.capitalize())
    }

    for (member in membersToPush) {
        checkMemberClashing(conflicts, context, member, membersToKeepAbstract, substitutor, targetClass, targetClassSymbol)
        checkSuperCalls(conflicts, context, member, membersToPush)
        checkExternalUsages(conflicts, member, targetClassSymbol)
        checkVisibility(conflicts, member, targetClass)
    }
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.checkMemberClashing(
    conflicts: MultiMap<PsiElement, String>,
    context: K2PushDownContext,
    member: KtNamedDeclaration,
    membersToKeepAbstract: List<KtNamedDeclaration>,
    substitutor: KaSubstitutor,
    targetClass: KtClassOrObject,
    targetClassSymbol: KaClassSymbol,
) {
    when (member) {
        is KtNamedFunction, is KtProperty -> {
            val memberDescriptor = member.symbol as KaCallableSymbol
            val clashingSymbol =
                targetClassSymbol.findCallableMemberBySignature(memberDescriptor.substitute(substitutor), ignoreReturnType = true)
            val clashingDeclaration = clashingSymbol?.psi as? KtNamedDeclaration
            if (clashingSymbol != null && clashingDeclaration != null) {
                if (memberDescriptor.modality != KaSymbolModality.ABSTRACT && member !in membersToKeepAbstract) {
                    val message = KotlinBundle.message(
                        "text.0.already.contains.1",
                        targetClassSymbol.renderForConflicts(analysisSession = this),
                        clashingSymbol.renderForConflicts(analysisSession = this),
                    )
                    conflicts.putValue(clashingDeclaration, StringUtil.capitalize(message))
                }
                if (!clashingDeclaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
                    val message = KotlinBundle.message(
                        "text.0.in.1.will.override.corresponding.member.of.2.after.refactoring",
                        clashingSymbol.renderForConflicts(analysisSession = this),
                        targetClassSymbol.renderForConflicts(analysisSession = this),
                        context.sourceClass.symbol.renderForConflicts(analysisSession = this),
                    )
                    conflicts.putValue(clashingDeclaration, StringUtil.capitalize(message))
                }
            }
        }

        is KtClassOrObject -> {
            targetClass.declarations
                .asSequence()
                .filterIsInstance<KtClassOrObject>()
                .firstOrNull { it.name == member.name }
                ?.let {
                    val message = KotlinBundle.message(
                        "text.0.already.contains.nested.class.1",
                        targetClassSymbol.renderForConflicts(analysisSession = this),
                        CommonRefactoringUtil.htmlEmphasize(member.name ?: "")
                    )
                    conflicts.putValue(it, message.capitalize())
                }
        }
    }
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.checkSuperCalls(
    conflicts: MultiMap<PsiElement, String>,
    context: K2PushDownContext,
    member: KtNamedDeclaration,
    membersToPush: ArrayList<KtNamedDeclaration>
) {
    member.accept(
        object : KtTreeVisitorVoid() {
            override fun visitSuperExpression(expression: KtSuperExpression) {
                val qualifiedExpression = expression.getQualifiedExpressionForReceiver() ?: return
                val refExpr = qualifiedExpression.selectorExpression.getCalleeExpressionIfAny() as? KtSimpleNameExpression ?: return
                for (descriptor in refExpr.mainReference.resolveToSymbols()) {
                    val memberSymbol = descriptor as? KaCallableSymbol ?: continue
                    val containingClass = memberSymbol.containingDeclaration as? KaClassSymbol ?: continue
                    if (!(context.sourceClass.symbol as KaClassSymbol).isSubClassOf(containingClass)) continue
                    val memberInSource = (context.sourceClass.symbol as KaClassSymbol).findCallableMemberBySignature(
                        memberSymbol.asSignature(),
                        ignoreReturnType = true
                    )?.psi
                        ?: continue
                    if (memberInSource !in membersToPush) {
                        conflicts.putValue(
                            qualifiedExpression,
                            KotlinBundle.message("text.pushed.member.will.not.be.available.in.0", qualifiedExpression.text)
                        )
                    }
                }
            }
        }
    )
}

private fun KaSession.checkExternalUsages(
    conflicts: MultiMap<PsiElement, String>,
    member: PsiElement,
    targetClassSymbol: KaClassSymbol,
) {
    for (ref in ReferencesSearch.search(member, member.resolveScope, false).findAll()) {
        val calleeExpr = ref.element as? KtSimpleNameExpression ?: continue
        val resolvedCall = calleeExpr.resolveToCall()?.singleFunctionCallOrNull() ?: continue
        val callElement = calleeExpr.parentOfType<KtCallExpression>() ?: continue
        val dispatchReceiver = resolvedCall.partiallyAppliedSymbol.dispatchReceiver
        if (dispatchReceiver == null) continue
        val receiverClassSymbol = dispatchReceiver.type.expandedSymbol ?: continue
        if (receiverClassSymbol != targetClassSymbol && !receiverClassSymbol.isSubClassOf(targetClassSymbol)) {
            conflicts.putValue(callElement, KotlinBundle.message("text.pushed.member.will.not.be.available.in.0", callElement.text))
        }
    }
}

private fun KaSession.checkVisibility(
    conflicts: MultiMap<PsiElement, String>,
    member: KtNamedDeclaration,
    targetClass: KtClassOrObject,
) {
    fun reportConflictIfAny(targetSymbol: KaDeclarationSymbol) {
        val target = targetSymbol.psi ?: return

        if (!isVisible(targetSymbol, targetClass)) {
            val message = KotlinBundle.message(
                "text.0.uses.1.which.is.not.accessible.from.2",
                member.symbol.renderForConflicts(analysisSession = this),
                targetSymbol.renderForConflicts(analysisSession = this),
                targetClass.symbol.renderForConflicts(analysisSession = this)
            )
            conflicts.putValue(target, message.capitalize())
        }
    }

    member.accept(
        object : KtTreeVisitorVoid() {
            override fun visitReferenceExpression(expression: KtReferenceExpression) {
                super.visitReferenceExpression(expression)

                expression.references
                    .flatMap { (it as? KtReference)?.resolveToSymbols() ?: emptyList() }
                    .filterIsInstance<KaDeclarationSymbol>()
                    .forEach(::reportConflictIfAny)

            }
        }
    )
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.isVisible(what: KaDeclarationSymbol, where: PsiElement): Boolean {
    val file = (where.containingFile as? KtFile)?.symbol ?: return false
    return createUseSiteVisibilityChecker(file, receiverExpression = null, where).isVisible(what)
}
