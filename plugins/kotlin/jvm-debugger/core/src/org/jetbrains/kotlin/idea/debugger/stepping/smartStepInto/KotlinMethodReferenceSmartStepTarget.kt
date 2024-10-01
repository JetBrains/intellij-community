// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.engine.MethodFilter
import com.intellij.psi.createSmartPointer
import com.intellij.util.Range
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.debugger.base.util.runDumbAnalyze
import org.jetbrains.kotlin.idea.debugger.core.getClassName
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
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
    override fun getClassName(): String? {
        val declaration = declarationPtr.getElementInReadAction() ?: return null
        return runDumbAnalyze(declaration, fallback = null) { declaration.getClassName() }
    }
}
