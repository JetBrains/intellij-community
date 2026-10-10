// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.refactoring.modplan.ModPlanEdit
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo
import org.jetbrains.kotlin.idea.base.psi.deleteValueArgument
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

class SafeDeleteValueArgumentListUsageInfo(
    parameter: PsiElement,
    vararg valueArguments: KtValueArgument
) : SafeDeleteReferenceSimpleDeleteUsageInfo(valueArguments.first(), parameter, true) {
    private val valueArgumentPointers = valueArguments.map { it.createSmartPointer() }

    override fun deleteElement() {
        for (valueArgumentPointer in valueArgumentPointers) {
            val valueArgument = valueArgumentPointer.element ?: return
            deleteArgument(valueArgument)
        }
    }

    override fun asModPlanEdit(): ModPlanEdit? {
        val arguments = valueArgumentPointers.map { it.element ?: return null }
        if (findReferencedElement() == null) return null
        return ModPlanEdit.update(arguments) { copies ->
            for (copy in copies) {
                deleteArgument(copy as KtValueArgument)
            }
        }
    }
}

private fun deleteArgument(valueArgument: KtValueArgument) {
    val parent = valueArgument.parent
    if (parent is KtValueArgumentList) {
        parent.deleteValueArgument(valueArgument)
    } else {
        valueArgument.delete()
    }
}
