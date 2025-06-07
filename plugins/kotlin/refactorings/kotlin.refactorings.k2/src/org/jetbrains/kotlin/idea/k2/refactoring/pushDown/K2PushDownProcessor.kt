// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pushDown

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor.Empty
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allowAnalysisFromWriteActionInEdt
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.applyMarking
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.clearMarking
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.markElements
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.renderForConflicts
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.pushDown.KotlinPushDownProcessor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration

private data class PushDownTarget(
    val memberInfo: KotlinMemberInfo,
    val targetClassFqName: FqName,
)

/**
 * Represents a refactoring step that inserts a member into a target class during push down.
 *
 * Implementations are expected to operate on PSI only and must not perform analysis.
 *
 * @return the inserted [KtNamedDeclaration] if additional updates ([markElements] and [applyMarking]) are needed,
 *         or `null` if no further processing is required.
 *
 * @see markElements for recording elements requiring post-processing
 * @see applyMarking for applying delayed reference or type fixes
 */
internal fun interface PushDownAction {
    fun execute(): KtNamedDeclaration?
}

/**
 * Represents a refactoring step that removes the original member from the source class during push down.
 *
 * Implementations are expected to operate on PSI only and must not perform analysis.
 */
internal fun interface RemovalAction {
    fun execute()
}

private data class PushDownActionsContext(
    val pushDownActionsByTarget: MutableMap<PushDownTarget, PushDownAction> = mutableMapOf(),
    val removalActionsByMember: MutableMap<KotlinMemberInfo, RemovalAction> = mutableMapOf(),
    val markedElements: MutableList<KtElement> = ArrayList(),
)

internal class K2PushDownProcessor(
    project: Project,
    sourceClass: KtClass,
    membersToMove: List<KotlinMemberInfo>,
) : KotlinPushDownProcessor(project) {
    override val context: K2PushDownContext = K2PushDownContext(sourceClass, membersToMove)

    override fun renderSourceClassForConflicts(): String = analyze(context.sourceClass) {
        context.sourceClass.symbol.renderForConflicts(analysisSession = this)
    }

    override fun analyzePushDownConflicts(
        usages: Array<out UsageInfo>,
    ): MultiMap<PsiElement, String> = analyze(context.sourceClass) {
        analyzePushDownConflicts(context, usages)
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val actionsContext = PushDownActionsContext()
        try {
            prepareRefactoring(usages, actionsContext)
            executeRefactoring(usages, actionsContext)
        } finally {
            clearMarking(actionsContext.markedElements)
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun prepareRefactoring(
        usages: Array<out UsageInfo>,
        actionsContext: PushDownActionsContext,
    ) {
        val targetClasses = usages.mapNotNull { it.element as? KtClassOrObject }
        processTargetClasses(targetClasses, actionsContext)
    }

    @OptIn(KaExperimentalApi::class)
    private fun processTargetClasses(
        targetClasses: List<KtClassOrObject>,
        actionsContext: PushDownActionsContext,
    ) {
        val sourceClass = context.sourceClass
        allowAnalysisFromWriteActionInEdt(sourceClass) {
            targetClasses.forEach { targetClass ->
                val substitutor = createInheritanceTypeSubstitutor(
                    subClass = targetClass.symbol as KaClassSymbol,
                    superClass = sourceClass.symbol as KaClassSymbol,
                ) ?: Empty(token)
                processTargetClass(targetClass, substitutor, actionsContext)
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.processTargetClass(
        targetClass: KtClassOrObject,
        substitutor: KaSubstitutor,
        actionsContext: PushDownActionsContext,
    ) {
        val targetClassFqName = targetClass.fqName ?: return

        context.membersToMove.forEach { memberInfo ->
            registerPushDownAction(targetClass, memberInfo, targetClassFqName, substitutor, actionsContext)
            registerRemovalAction(memberInfo, substitutor, actionsContext)
            markElementsForRefactoring(memberInfo, targetClass, substitutor, actionsContext)
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.registerPushDownAction(
        targetClass: KtClassOrObject,
        memberInfo: KotlinMemberInfo,
        targetClassFqName: FqName,
        substitutor: KaSubstitutor,
        actionsContext: PushDownActionsContext,
    ) {
        createPushDownAction(
            context.sourceClass,
            memberInfo,
            targetClass,
            substitutor,
        )?.let { pushDownAction ->
            actionsContext.pushDownActionsByTarget[PushDownTarget(memberInfo, targetClassFqName)] = pushDownAction
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.registerRemovalAction(
        memberInfo: KotlinMemberInfo,
        substitutor: KaSubstitutor,
        actionsContext: PushDownActionsContext,
    ) {
        createRemoveOriginalMemberAction(
            context.sourceClass,
            memberInfo,
            substitutor,
        )?.let { removeAction ->
            actionsContext.removalActionsByMember[memberInfo] = removeAction
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.markElementsForRefactoring(
        memberInfo: KotlinMemberInfo,
        targetClass: KtClassOrObject,
        substitutor: KaSubstitutor,
        actionsContext: PushDownActionsContext,
    ) {
        actionsContext.markedElements += markElements(
            memberInfo.member,
            context.sourceClass,
            targetClass,
            substitutor,
        )
    }

    private fun executeRefactoring(
        usages: Array<out UsageInfo>,
        actionsContext: PushDownActionsContext,
    ) {
        usages.map { it.element }
            .filterIsInstance<KtClassOrObject>()
            .forEach { targetClass ->
                val fqName = targetClass.fqName ?: return@forEach
                context.membersToMove.forEach { member ->
                    val target = PushDownTarget(member, fqName)
                    val action = actionsContext.pushDownActionsByTarget[target] ?: return@forEach
                    val added = action.execute() ?: return@forEach
                    applyMarking(added, targetClass)
                }
            }

        context.membersToMove.forEach { memberInfo ->
            actionsContext.removalActionsByMember[memberInfo]?.execute()
        }
    }
}
