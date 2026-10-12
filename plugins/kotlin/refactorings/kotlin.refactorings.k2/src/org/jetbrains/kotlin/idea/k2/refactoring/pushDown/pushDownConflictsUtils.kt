// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pushDown

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbols
import org.jetbrains.kotlin.analysis.api.projectStructure.kaModule
import org.jetbrains.kotlin.analysis.api.resolution.function
import org.jetbrains.kotlin.analysis.api.resolution.single
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.signatures.asSignature
import org.jetbrains.kotlin.analysis.api.signatures.substitute
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.containingDeclaration
import org.jetbrains.kotlin.analysis.api.symbols.isSubClassOf
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor
import org.jetbrains.kotlin.analysis.api.types.createInheritanceTypeSubstitutor
import org.jetbrains.kotlin.analysis.api.types.emptySubstitutor
import org.jetbrains.kotlin.analysis.api.types.expandedSymbol
import org.jetbrains.kotlin.analysis.api.visibility.createUseSiteVisibilityChecker
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.findCallableMemberBySignature
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.renderForConflicts
import org.jetbrains.kotlin.idea.refactoring.isCompanionMemberOf
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.tryResolveExpressionCall
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny

context(session: KaSession)
internal fun analyzePushDownConflicts(
    context: K2PushDownContext,
    usages: Array<out UsageInfo>,
    inlineSuperclass: Boolean = false,
): MultiMap<PsiElement, String> {
    val targetClasses = usages.filterNot { it is ClassMemberUsage }.mapNotNull { it.element?.unwrapped }

    val conflicts = MultiMap<PsiElement, String>()

    val membersToPush = ArrayList<KtNamedDeclaration>()
    val membersToKeepAbstract = ArrayList<KtNamedDeclaration>()
    for (info in context.membersToMove) {
        val member = info.member
        if (!info.isChecked || member is KtSuperTypeListEntry) continue

        if (member !is KtNamedDeclaration) continue
        membersToPush += member
        if ((member is KtNamedFunction || member is KtProperty) && info.isToAbstract && (member.symbol as? KaCallableSymbol)?.modality != KaSymbolModality.ABSTRACT) {
            membersToKeepAbstract += member
        }
    }

    for (targetClass in targetClasses) {
        analyze(targetClass.kaModule) {
            checkConflicts(conflicts, context, targetClass, membersToKeepAbstract, membersToPush, inlineSuperclass)
        }
    }

    if (targetClasses.size > 1) {
        usages.filterIsInstance<ClassMemberUsage>().mapNotNull { usage ->
            val element = usage.element ?: return@mapNotNull null
            var current = element
            while (current.parent is KtCallExpression || current.parent is KtDotQualifiedExpression || current.parent is KtUserType) {
                current = current.parent
            }
            current
        }.distinct().forEach {
            conflicts.putValue(it, KotlinBundle.message("text.pushed.member.will.not.be.available.in.0", it.text))
        }
    }
    return conflicts
}

context(session: KaSession)
internal fun checkConflicts(
    conflicts: MultiMap<PsiElement, String>,
    context: K2PushDownContext,
    targetClass: PsiElement,
    membersToKeepAbstract: List<KtNamedDeclaration>,
    membersToPush: ArrayList<KtNamedDeclaration>,
    inlineSuperclass: Boolean = false,
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
    ) ?: emptySubstitutor

    if (!context.sourceClass.isInterface() && targetClass is KtClass && targetClass.isInterface()) {
        val message = KotlinBundle.message(
            "text.0.inherits.from.1.it.will.not.be.affected.by.refactoring",
            targetClassSymbol.renderForConflicts(),
            context.sourceClass.symbol.renderForConflicts(),
        )
        conflicts.putValue(targetClass, message.capitalize())
    }

    for (member in membersToPush) {
        if (targetClass is KtObjectDeclaration && member is KtClass && member.hasModifier(KtTokens.INNER_KEYWORD)) {
            val message = KotlinBundle.message("text.inner.class.0.cannot.be.moved.to.object", member.symbol.renderForConflicts())
            conflicts.putValue(
                member,
                message.replaceFirstChar { it.uppercaseChar() },
            )
        }
        checkMemberClashing(conflicts, context, member, membersToKeepAbstract, substitutor, targetClass, targetClassSymbol, inlineSuperclass)
        checkSuperCalls(conflicts, context, member, membersToPush)
        if (!inlineSuperclass) checkExternalUsages(conflicts, member, targetClassSymbol)
        checkVisibility(conflicts, member, targetClass, if (inlineSuperclass) membersToPush else emptyList())
    }
    if (inlineSuperclass) {
        for (element in sourceClass.getAnonymousInitializers() + sourceClass.superTypeListEntries) {
            checkVisibility(conflicts, element, targetClass, membersToPush)
        }
    }
}

context(session: KaSession)
private fun checkMemberClashing(
    conflicts: MultiMap<PsiElement, String>,
    context: K2PushDownContext,
    member: KtNamedDeclaration,
    membersToKeepAbstract: List<KtNamedDeclaration>,
    substitutor: KaSubstitutor,
    targetClass: KtClassOrObject,
    targetClassSymbol: KaClassSymbol,
    inlineSuperclass: Boolean,
) {
    when (member) {
        is KtNamedFunction, is KtProperty -> {
            val callableSymbol = member.symbol as KaCallableSymbol
            val targetMemberClassSymbol = if (member.isCompanionMemberOf(context.sourceClass) && targetClass is KtClass) {
                targetClass.companionObjects.firstOrNull()?.symbol
            } else {
                targetClassSymbol
            }
            val clashingSymbol = targetMemberClassSymbol?.findCallableMemberBySignature(
                callableSymbol.substitute(substitutor), ignoreReturnType = true
            )
            val clashingDeclaration = clashingSymbol?.psi as? KtNamedDeclaration
            if (clashingSymbol != null && clashingDeclaration != null) {
                val overriddenFunction = inlineSuperclass && member is KtNamedFunction &&
                        clashingDeclaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)
                if (callableSymbol.modality != KaSymbolModality.ABSTRACT && member !in membersToKeepAbstract && !overriddenFunction) {
                    val message = KotlinBundle.message(
                        "text.0.already.contains.1",
                        targetClassSymbol.renderForConflicts(),
                        clashingSymbol.renderForConflicts(),
                    )
                    conflicts.putValue(clashingDeclaration, StringUtil.capitalize(message))
                }
                if (!member.isCompanionMemberOf(context.sourceClass) && !clashingDeclaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
                    val message = KotlinBundle.message(
                        "text.0.in.1.will.override.corresponding.member.of.2.after.refactoring",
                        clashingSymbol.renderForConflicts(),
                        targetClassSymbol.renderForConflicts(),
                        context.sourceClass.symbol.renderForConflicts(),
                    )
                    conflicts.putValue(clashingDeclaration, StringUtil.capitalize(message))
                }
            }
        }

        is KtClassOrObject -> {
            if (member is KtObjectDeclaration && member.isCompanion() && targetClass is KtClass) {
                val targetCompanion = targetClass.companionObjects.firstOrNull()
                if (targetCompanion != null) {
                    val targetCompanionSymbol = targetCompanion.symbol
                    member.declarations.filterIsInstance<KtNamedDeclaration>().forEach { nestedMember ->
                        checkMemberClashing(
                            conflicts, context, nestedMember, membersToKeepAbstract, substitutor,
                            targetCompanion, targetCompanionSymbol, inlineSuperclass,
                        )
                    }
                    return
                }
            }
            targetClass.declarations
                .asSequence()
                .filterIsInstance<KtClassOrObject>()
                .firstOrNull { it.name == member.name }
                ?.let {
                    val message = KotlinBundle.message(
                        "text.0.already.contains.nested.class.1",
                        targetClassSymbol.renderForConflicts(),
                        CommonRefactoringUtil.htmlEmphasize(member.name ?: "")
                    )
                    conflicts.putValue(it, message.capitalize())
                }
        }
    }
}

context(session: KaSession)
private fun checkSuperCalls(
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
                for (symbol in refExpr.mainReference.resolveToSymbols()) {
                    val callableSymbol = symbol as? KaCallableSymbol ?: continue
                    val containingClass = callableSymbol.containingDeclaration as? KaClassSymbol ?: continue
                    if (!(context.sourceClass.symbol as KaClassSymbol).isSubClassOf(containingClass)) continue
                    val memberInSource = (context.sourceClass.symbol as KaClassSymbol).findCallableMemberBySignature(
                        callableSymbol.asSignature(),
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

context(session: KaSession)
private fun checkExternalUsages(
    conflicts: MultiMap<PsiElement, String>,
    member: PsiElement,
    targetClassSymbol: KaClassSymbol,
) {
    for (ref in ReferencesSearch.search(member, member.resolveScope, false).findAll()) {
        val calleeExpr = ref.element as? KtSimpleNameExpression ?: continue
        val resolvedCall = calleeExpr.tryResolveExpressionCall()?.single?.function ?: continue
        val callElement = calleeExpr.parentOfType<KtCallExpression>() ?: continue
        val dispatchReceiver = resolvedCall.dispatchReceiver
        if (dispatchReceiver == null) continue
        val receiverClassSymbol = dispatchReceiver.type.expandedSymbol ?: continue
        if (receiverClassSymbol != targetClassSymbol && !receiverClassSymbol.isSubClassOf(targetClassSymbol)) {
            conflicts.putValue(callElement, KotlinBundle.message("text.pushed.member.will.not.be.available.in.0", callElement.text))
        }
    }
}

context(session: KaSession)
private fun checkVisibility(
    conflicts: MultiMap<PsiElement, String>,
    member: KtElement,
    targetClass: KtClassOrObject,
    movedMembers: List<KtNamedDeclaration>,
) {
    val declaration = member as? KtNamedDeclaration ?: member.parentOfType<KtClassOrObject>() ?: return
    fun reportConflictIfAny(targetSymbol: KaDeclarationSymbol) {
        val target = targetSymbol.psi ?: return
        if (PsiTreeUtil.isAncestor(member, target, false)) return
        if (movedMembers.any { PsiTreeUtil.isAncestor(it, target, false) }) return

        if (!isVisible(targetSymbol, targetClass)) {
            val message = KotlinBundle.message(
                "text.0.uses.1.which.is.not.accessible.from.2",
                declaration.symbol.renderForConflicts(),
                targetSymbol.renderForConflicts(),
                targetClass.symbol.renderForConflicts()
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

context(session: KaSession)
private fun isVisible(what: KaDeclarationSymbol, where: PsiElement): Boolean {
    val file = (where.containingFile as? KtFile)?.symbol ?: return false
    return createUseSiteVisibilityChecker(file, receiverExpression = null, where).isVisible(what)
}
