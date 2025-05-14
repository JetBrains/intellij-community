// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pushDown

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import kotlinx.coroutines.Runnable
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor
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
import java.util.concurrent.Callable

private data class PushMemberDownTarget(
    val memberInfo: KotlinMemberInfo,
    val targetClassFqName: FqName,
)

internal class K2PushDownProcessor(
    project: Project,
    sourceClass: KtClass,
    membersToMove: List<KotlinMemberInfo>,
) : KotlinPushDownProcessor(project) {
    override val context: K2PushDownContext = K2PushDownContext(sourceClass, membersToMove)
    private val pushDownActionsByTarget: MutableMap<PushMemberDownTarget, Callable<KtNamedDeclaration>> = mutableMapOf()
    private val removalActionsByMember: MutableMap<KotlinMemberInfo, Runnable> = mutableMapOf()
    private val markedElements = ArrayList<KtElement>()

    override fun renderSourceClassForConflicts(): String =
        analyze(context.sourceClass) { context.sourceClass.symbol.renderForConflicts(analysisSession = this)}

    override fun findUsages(): Array<out UsageInfo> {
        val usages = super.findUsages()

        analyze(context.sourceClass) {
            usages.map { it.element }
                .filterIsInstance<KtClassOrObject>()
                .forEach { targetClass ->
                    preparePushDownForTargetClass(targetClass)
                }
        }

        return usages
    }

    override fun analyzePushDownConflicts(
        usages: Array<out UsageInfo>
    ): MultiMap<PsiElement, String> =
        analyze(context.sourceClass) { analyzePushDownConflicts(context, usages) }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.preparePushDownForTargetClass(targetClass: KtClassOrObject) {
        val substitutor = createInheritanceTypeSubstitutor(
            subClass = targetClass.symbol as KaClassSymbol,
            superClass = context.sourceClass.symbol as KaClassSymbol,
        ) ?: KaSubstitutor.Empty(token)

        context.membersToMove.forEach { memberInfo ->
            processMemberForTargetClass(memberInfo, targetClass, substitutor)
            markedElements += markElements(memberInfo.member, context.sourceClass, targetClass, substitutor)
        }
    }

    override fun pushDownToClass(targetClass: KtClassOrObject) {
        val targetClassFqName = targetClass.fqName
        if (targetClassFqName == null) return
        context.membersToMove.forEach { memberInfo ->
            val addedMember = pushDownActionsByTarget[PushMemberDownTarget(memberInfo, targetClassFqName)]?.call() ?: return@forEach
            applyMarking(addedMember, targetClass)
        }
    }

    override fun removeOriginalMembers() {
        context.membersToMove.forEach { memberInfo ->
            removalActionsByMember[memberInfo]?.run()
        }
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        try {
            usages.mapNotNull { it.element as? KtClassOrObject }.forEach { pushDownToClass(it) }
            removeOriginalMembers()
        } finally {
            clearMarking(markedElements)
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.processMemberForTargetClass(
        memberInfo: KotlinMemberInfo,
        targetClass: KtClassOrObject,
        substitutor: KaSubstitutor,
    ) {
        targetClass.fqName?.let { fqName ->
            createPushDownAction(
                context.sourceClass,
                memberInfo,
                targetClass,
                substitutor,
            )?.let { pushDownAction ->
                pushDownActionsByTarget[PushMemberDownTarget(memberInfo, fqName)] = pushDownAction
            }
        }

        createRemoveOriginalMemberAction(
            context.sourceClass,
            memberInfo,
            substitutor,
        )?.let { removeOriginalMemberAction ->
            removalActionsByMember[memberInfo] = removeOriginalMemberAction
        }
    }
}
