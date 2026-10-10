// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.psi.PsiElement
import com.intellij.refactoring.modplan.ModPlanEdit
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteUsage
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceUsageInfo
import org.jetbrains.kotlin.idea.base.psi.removeSuperType
import org.jetbrains.kotlin.psi.KtSuperTypeEntry
import org.jetbrains.kotlin.psi.KtSuperTypeList

class SafeDeleteSuperTypeUsageInfo(
    entry: KtSuperTypeEntry,
    referencedElement: PsiElement
) : SafeDeleteReferenceUsageInfo(entry, referencedElement, true), ModPlanSafeDeleteUsage {
    private val entry: KtSuperTypeEntry?
        get() = element as? KtSuperTypeEntry

    override fun deleteElement() {
        val entry = entry ?: return
        removeSuperTypeEntry(entry)
    }

    override fun asModPlanEdit(): ModPlanEdit? {
        val entry = entry ?: return null
        if (findReferencedElement() == null) return null
        return ModPlanEdit.update(entry, ::removeSuperTypeEntry)
    }
}

private fun removeSuperTypeEntry(entry: KtSuperTypeEntry) {
    (entry.parent as? KtSuperTypeList)?.removeSuperType(entry)
}
