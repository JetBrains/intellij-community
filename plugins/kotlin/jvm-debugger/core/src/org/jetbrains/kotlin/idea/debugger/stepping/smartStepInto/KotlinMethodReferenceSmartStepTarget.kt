// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.util.Range
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import javax.swing.Icon

class KotlinMethodReferenceSmartStepTarget(
    highlightElement: KtCallableReferenceExpression,
    lines: Range<Int>,
    label: String,
    val targetMethodName: String,
    val declaration: KtDeclarationWithBody
) : KotlinSmartStepTarget(label, highlightElement, true, lines) {
    override fun createMethodFilter() =
        KotlinMethodReferenceFilter(this)

    override fun getIcon(): Icon? = KotlinIcons.FUNCTION
}
