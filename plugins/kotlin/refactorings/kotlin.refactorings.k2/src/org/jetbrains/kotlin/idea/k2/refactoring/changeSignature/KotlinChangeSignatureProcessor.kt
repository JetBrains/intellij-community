// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessorBase
import com.intellij.refactoring.changeSignature.ChangeSignatureUsageProcessor
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usageView.UsageViewUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinUsagesViewDescriptor

open class KotlinChangeSignatureProcessor(project: Project, changeInfo: KotlinChangeInfo) : ChangeSignatureProcessorBase(project, changeInfo) {
    init {
        // we must force collecting references to other parameters now before the signature is changed
        changeInfo.newParameters.forEach { it.collectDefaultValueParameterReferences(changeInfo.method) }
    }

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo?>): UsageViewDescriptor {
        val method = myChangeInfo.method
        return KotlinUsagesViewDescriptor(method, RefactoringBundle.message("0.to.change.signature", UsageViewUtil.getType(method)))
    }

    protected override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val usageProcessors = ChangeSignatureUsageProcessor.EP_NAME.extensions

        if (!usageProcessors.all { it.setupDefaultValues(myChangeInfo, refUsages, myProject) }) return false

        val conflictDescriptions = object : MultiMap<PsiElement, String>() {
            override fun createCollection() = LinkedHashSet<String>()
        }

        collectConflictsFromExtensions(refUsages, conflictDescriptions, myChangeInfo)

        val usages = refUsages.get() ?: return false
        val usagesSet = usages.toHashSet()

        RenameUtil.addConflictDescriptions(usages, conflictDescriptions)
        val conflictUsages = RenameUtil.removeConflictUsages(usagesSet)
        if (conflictUsages != null) {
            refUsages.set(usagesSet.toTypedArray())
        }
        return showConflicts(conflictDescriptions, usagesSet.toTypedArray())
    }
}