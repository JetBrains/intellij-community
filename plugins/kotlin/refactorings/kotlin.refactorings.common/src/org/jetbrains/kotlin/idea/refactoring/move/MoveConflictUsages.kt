// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap

data class MoveConflictUsages(val conflicts: MultiMap<PsiElement, String>, val usages: Array<UsageInfo>) {
    companion object {
        fun preprocess(refUsages: Ref<Array<UsageInfo>>): Pair<MultiMap<PsiElement, String>, Array<UsageInfo>> {
            val usages: Array<UsageInfo> = refUsages.get()

            val (conflictUsages, usagesToProcess) = usages.partition { it is MoveConflictUsageInfo }

            val conflicts = MultiMap<PsiElement, String>()
            for (conflictUsage in conflictUsages) {
                conflicts.putValues(conflictUsage.element, (conflictUsage as MoveConflictUsageInfo).messages)
            }

            refUsages.set(usagesToProcess.toTypedArray())

            return Pair(conflicts, usages)
        }
    }
}