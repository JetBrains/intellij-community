// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pushDown

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.classSymbol
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor
import org.jetbrains.kotlin.analysis.api.types.createInheritanceTypeSubstitutor
import org.jetbrains.kotlin.analysis.api.types.emptySubstitutor
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allowAnalysisFromWriteActionInEdt
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.applyMarking
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.clearMarking
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.markElements
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.markTypeReferences
import org.jetbrains.kotlin.idea.k2.refactoring.pullUp.rebindTypeReferences
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.pushDown.KotlinPushDownProcessor
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

private data class PushDownTarget(
    val memberInfo: KotlinMemberInfo,
    val targetClassFqName: FqName,
)

internal class ClassMemberUsage(
    element: PsiElement,
    val sourceNestedClass: KtClassOrObject,
    val sourceDeclaration: KtNamedDeclaration,
) : UsageInfo(element) {
    init {
        sourceDeclaration.putCopyableUserData(SOURCE_DECLARATION_KEY, sourceDeclaration)
    }
}

private val SOURCE_DECLARATION_KEY = Key.create<KtNamedDeclaration>("kotlin.push.down.source.declaration")

internal fun KtNamedDeclaration.sourceDeclarationForPushDown(): KtNamedDeclaration? = getCopyableUserData(SOURCE_DECLARATION_KEY)

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
    private val inlineSuperclass: Boolean = false,
) : KotlinPushDownProcessor(project) {
    override val context: K2PushDownContext = K2PushDownContext(sourceClass, membersToMove)

    override fun findUsages(): Array<out UsageInfo> {
        return (super.findUsages().asList() + classMemberUsages()).toTypedArray()
    }

    fun classMemberUsages(): List<ClassMemberUsage> {
        val memberUsages = context.membersToMove
            .mapNotNull { it.member as? KtClassOrObject }
            .flatMap(::findClassMemberUsages)
        return memberUsages
    }

    private fun findClassMemberUsages(classOrObject: KtClassOrObject): List<ClassMemberUsage> {
        val usages = ArrayList<ClassMemberUsage>()

        fun collect(declaration: KtNamedDeclaration) {
            ReferencesSearch.search(declaration).forEach { reference ->
                val element = reference.element
                if (!PsiTreeUtil.isAncestor(classOrObject, element, false)) {
                    usages += ClassMemberUsage(element, classOrObject, declaration)
                }
            }
            if (declaration is KtClassOrObject) {
                declaration.declarations.filterIsInstance<KtNamedDeclaration>().forEach { collect(it) }
            }
        }

        collect(classOrObject)
        return usages
    }

    override fun analyzePushDownConflicts(
        usages: Array<out UsageInfo>,
    ): MultiMap<PsiElement, String> = analyze(context.sourceClass) {
        analyzePushDownConflicts(context, usages)
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        pushDownToClasses(usages)
    }

    internal fun pushDownToClasses(
        usages: Array<out UsageInfo>,
        removeOriginalMembers: Boolean = true,
        onMemberMoved: (KtNamedDeclaration, KtNamedDeclaration, KtClassOrObject) -> Unit = { _, _, _ -> },
    ) {
        val actionsContext = PushDownActionsContext()
        try {
            prepareRefactoring(usages, actionsContext)
            executeRefactoring(usages, actionsContext, removeOriginalMembers, onMemberMoved)
        } finally {
            clearMarking(actionsContext.markedElements)
        }
    }

    private fun prepareRefactoring(
        usages: Array<out UsageInfo>,
        actionsContext: PushDownActionsContext,
    ) {
        val targetClasses = usages.mapNotNull { it.element as? KtClassOrObject }
        processTargetClasses(targetClasses, actionsContext)
    }

    private fun processTargetClasses(
        targetClasses: List<KtClassOrObject>,
        actionsContext: PushDownActionsContext,
    ) {
        val sourceClass = context.sourceClass
        if (targetClasses.isEmpty()) {
            // No inheritors exist, but we still register removal actions to delete members
            // from the source class. This ensures the refactoring behaves as indicated in
            // the user-facing warning dialog.
            // See: K2PushDownTestGenerated.K2K.testNoInheritors
            context.membersToMove.forEach { memberInfo ->
                allowAnalysisFromWriteActionInEdt(sourceClass) {
                    registerRemovalAction(memberInfo, emptySubstitutor, actionsContext)
                }
            }
        } else {
            targetClasses.forEach { targetClass ->
                allowAnalysisFromWriteActionInEdt(targetClass) {
                    val substitutor = createInheritanceTypeSubstitutor(
                        subClass = targetClass.classSymbol ?: return@forEach,
                        superClass = sourceClass.classSymbol ?: return@forEach,
                    ) ?: emptySubstitutor
                    processTargetClass(targetClass, substitutor, actionsContext)
                }
            }
        }
    }

    context(session: KaSession)
    private fun processTargetClass(
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

    context(session: KaSession)
    private fun registerPushDownAction(
        targetClass: KtClassOrObject,
        memberInfo: KotlinMemberInfo,
        targetClassFqName: FqName,
        substitutor: KaSubstitutor,
        actionsContext: PushDownActionsContext,
    ) {
        createPushDownAction(
            memberInfo,
            targetClass,
            substitutor,
            inlineSuperclass,
        )?.let { pushDownAction ->
            actionsContext.pushDownActionsByTarget[PushDownTarget(memberInfo, targetClassFqName)] = pushDownAction
        }
    }

    context(session: KaSession)
    private fun registerRemovalAction(
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

    context(session: KaSession)
    private fun markElementsForRefactoring(
        memberInfo: KotlinMemberInfo,
        targetClass: KtClassOrObject,
        substitutor: KaSubstitutor,
        actionsContext: PushDownActionsContext,
    ) {
        val member = memberInfo.member as? KtNamedDeclaration ?: return
        actionsContext.markedElements += markElements(
            member,
            context.sourceClass,
            targetClass,
            substitutor,
        )
        actionsContext.markedElements += markTypeReferences(member)
    }

    private fun executeRefactoring(
        usages: Array<out UsageInfo>,
        actionsContext: PushDownActionsContext,
        removeOriginalMembers: Boolean,
        onMemberMoved: (KtNamedDeclaration, KtNamedDeclaration, KtClassOrObject) -> Unit,
    ) {
        val shouldRebind = removeOriginalMembers && usages.count { it is SubclassUsage } == 1
        val membersToMove = context.membersToMove.sortedBy { (it.member as? KtObjectDeclaration)?.isCompanion() == true }
        usages.map { it.element }
            .filterIsInstance<KtClassOrObject>()
            .forEach { targetClass ->
                val fqName = targetClass.fqName ?: return@forEach
                membersToMove.forEach { member ->
                    val target = PushDownTarget(member, fqName)
                    val action = actionsContext.pushDownActionsByTarget[target] ?: return@forEach
                    val memberUsages = usages.filterIsInstance<ClassMemberUsage>().filter { usage ->
                        usage.sourceNestedClass == member.member && (!inlineSuperclass ||
                                usage.sourceDeclaration != member.member && usage.element?.let {
                                    !PsiTreeUtil.isAncestor(context.sourceClass, it, false)
                                } == true)
                    }
                    val added = action.execute() ?: return@forEach
                    applyMarking(added, targetClass)
                    rebindTypeReferences(added, membersToMove.map { it.member })
                    onMemberMoved(member.member as KtNamedDeclaration, added, targetClass)
                    if (shouldRebind && added is KtClassOrObject) {
                        rebindClassMemberUsages(added, memberUsages)
                    }
                }
            }

        if (removeOriginalMembers) {
            context.membersToMove.forEach { memberInfo ->
                actionsContext.removalActionsByMember[memberInfo]?.execute()
            }
        }
    }

    private fun rebindClassMemberUsages(added: KtClassOrObject, usages: List<ClassMemberUsage>) {
        val usagesByDeclaration = usages.groupBy { it.sourceDeclaration }

        for (targetDeclaration in added.collectDescendantsOfType<KtNamedDeclaration>()) {
            val sourceDeclaration = targetDeclaration.getCopyableUserData(SOURCE_DECLARATION_KEY) ?: continue
            val classMemberUsages = usagesByDeclaration[sourceDeclaration] ?: continue
            try {
                for (usage in classMemberUsages.sortedWith(Comparator { o1, o2 -> o1.compareToByStartOffset(o2) })) {
                    val element = usage.element ?: continue
                    when (element) {
                        is KtElement -> {
                            if (element.containingFile == targetDeclaration.containingFile || element !is KtSimpleNameExpression) {
                                element.mainReference?.bindToElement(targetDeclaration)
                            } else {
                                element.mainReference.bindToElement(targetDeclaration, KtSimpleNameReference.ShorteningMode.NO_SHORTENING)
                            }
                        }

                        is PsiJavaCodeReferenceElement -> {
                            val lightElement = targetDeclaration.toLightElements().firstOrNull() ?: continue
                            element.bindToElement(lightElement)
                        }
                    }
                }
            } finally {
                targetDeclaration.putCopyableUserData(SOURCE_DECLARATION_KEY, null)
            }
        }
    }
}
