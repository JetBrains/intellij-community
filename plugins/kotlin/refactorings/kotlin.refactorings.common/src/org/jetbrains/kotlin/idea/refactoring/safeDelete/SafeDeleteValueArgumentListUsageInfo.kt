// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.psi.PsiElement
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

class SafeDeleteValueArgumentListUsageInfo(
    parameter: PsiElement,
    vararg valueArguments: KtValueArgument
) : SafeDeleteReferenceSimpleDeleteUsageInfo(valueArguments.first(), parameter, true) {
    private val valueArgumentPointers = valueArguments.map { it.createSmartPointer() }

    override fun deleteElement() {
        for (valueArgumentPointer in valueArgumentPointers) {
            val valueArgument = valueArgumentPointer.element ?: return
            val parent = valueArgument.parent
            if (parent is KtValueArgumentList) {
                parent.removeArgument(valueArgument)
            } else {
                valueArgument.delete()
            }
        }
    }
}
