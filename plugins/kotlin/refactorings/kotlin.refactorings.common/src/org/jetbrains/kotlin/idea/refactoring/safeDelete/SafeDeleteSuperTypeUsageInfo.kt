// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.psi.PsiElement
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceUsageInfo
import org.jetbrains.kotlin.psi.KtSuperTypeEntry
import org.jetbrains.kotlin.psi.KtSuperTypeList

class SafeDeleteSuperTypeUsageInfo(
    entry: KtSuperTypeEntry,
    referencedElement: PsiElement
) : SafeDeleteReferenceUsageInfo(entry, referencedElement, true) {
    private val entry: KtSuperTypeEntry?
        get() = element as? KtSuperTypeEntry

    override fun deleteElement() {
        val entry = entry ?: return
        (entry.parent as? KtSuperTypeList)?.removeEntry(entry)
    }
}