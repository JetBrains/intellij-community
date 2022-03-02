// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.engine.MethodFilter
import com.intellij.util.Range
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import javax.swing.Icon

class KotlinMethodReferenceSmartStepTarget(
    lines: Range<Int>,
    highlightElement: KtCallableReferenceExpression,
    label: String,
    declaration: KtDeclarationWithBody,
    private val methodInfo: CallableMemberInfo
) : KotlinSmartStepTarget(label, highlightElement, true, lines) {
    private val declarationPtr = declaration.createSmartPointer()

    override fun createMethodFilter(): MethodFilter {
        val declaration = declarationPtr.getElementInReadAction()
        return KotlinMethodReferenceFilter(declaration, callingExpressionLines, methodInfo)
    }

    override fun getIcon(): Icon? = KotlinIcons.FUNCTION
}
