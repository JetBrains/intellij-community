// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.psi.PsiElement
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo
import org.jetbrains.kotlin.idea.refactoring.deleteBracesAroundEmptyList
import org.jetbrains.kotlin.idea.refactoring.deleteSeparatingComma
import org.jetbrains.kotlin.psi.KtTypeProjection

class SafeDeleteTypeArgumentUsageInfo(
    projection: KtTypeProjection,
    referenceElement: PsiElement
) : SafeDeleteReferenceSimpleDeleteUsageInfo(projection, referenceElement, true) {
    override fun deleteElement() {
        val e = element
        if (e != null) {
            deleteSeparatingComma(e)
            deleteBracesAroundEmptyList(e)

            e.delete()
        }
    }
}
