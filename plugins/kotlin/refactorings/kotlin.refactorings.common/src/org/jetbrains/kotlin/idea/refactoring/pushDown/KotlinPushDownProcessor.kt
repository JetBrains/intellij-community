// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.pushDown

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.util.runSynchronouslyWithProgress
import org.jetbrains.kotlin.idea.search.declarationsSearch.HierarchySearchRequest
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchInheritors
import org.jetbrains.kotlin.psi.KtClassOrObject

abstract class KotlinPushDownProcessor(
    project: Project,
) : BaseRefactoringProcessor(project) {
    protected abstract val context: KotlinPushDownContext

    inner class UsageViewDescriptorImpl : UsageViewDescriptor {
        override fun getProcessedElementsHeader() = RefactoringBundle.message("push.down.members.elements.header")

        override fun getElements() = arrayOf(context.sourceClass)

        override fun getCodeReferencesText(usagesCount: Int, filesCount: Int) =
            RefactoringBundle.message("classes.to.push.down.members.to", UsageViewBundle.getReferencesString(usagesCount, filesCount))

        override fun getCommentReferencesText(usagesCount: Int, filesCount: Int) = null
    }

    class SubclassUsage(element: PsiElement) : UsageInfo(element)

    override fun getCommandName() = PUSH_MEMBERS_DOWN

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>) = UsageViewDescriptorImpl()

    override fun getBeforeData() = RefactoringEventData().apply {
        addElement(context.sourceClass)
        addElements(context.membersToMove.map { it.member }.toTypedArray())
    }

    override fun getAfterData(usages: Array<out UsageInfo>) = RefactoringEventData().apply {
        addElements(usages.mapNotNull { it.element as? KtClassOrObject })
    }

    protected override fun findUsages(): Array<out UsageInfo> {
        return HierarchySearchRequest(context.sourceClass, context.sourceClass.useScope, false).searchInheritors()
            .asIterable()
            .mapNotNull { it.unwrapped }
            .map(::SubclassUsage)
            .toTypedArray()
    }

    protected override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val usages = refUsages.get() ?: UsageInfo.EMPTY_ARRAY
        if (usages.isEmpty()) {
            val message = KotlinBundle.message("text.0.have.no.inheritors.warning", renderSourceClassForConflicts())
            val answer = Messages.showYesNoDialog(message.capitalize(), PUSH_MEMBERS_DOWN, Messages.getWarningIcon())
            if (answer == Messages.NO) return false
        }

        val conflicts = myProject.runSynchronouslyWithProgress(RefactoringBundle.message("detecting.possible.conflicts"), true) {
            runReadAction { analyzePushDownConflicts(usages) }
        } ?: return false

        return showConflicts(conflicts, usages)
    }

    protected abstract fun renderSourceClassForConflicts(): String

    protected abstract fun analyzePushDownConflicts(
        usages: Array<out UsageInfo>,
    ): MultiMap<PsiElement, String>

    protected abstract fun pushDownToClass(
        targetClass: KtClassOrObject,
    )

    protected abstract fun removeOriginalMembers()

}
