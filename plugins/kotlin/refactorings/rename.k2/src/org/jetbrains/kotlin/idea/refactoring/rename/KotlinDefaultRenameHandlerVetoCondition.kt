// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtElement

/**
 * We need to disable the default rename handler, otherwise it gets in the way
 * every time rename refactoring is performed. This API is the only way
 * to prevent [com.intellij.refactoring.rename.RenameHandlerRegistry.myDefaultElementRenameHandler]
 * from getting in the way.
 */
internal class KotlinDefaultRenameHandlerVetoCondition : Condition<PsiElement> {
    override fun value(element: PsiElement): Boolean = element is KtElement
}
