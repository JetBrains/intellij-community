// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.actions.SmartStepTarget
import com.intellij.debugger.engine.MethodFilter
import com.intellij.psi.PsiElement
import com.intellij.util.Range

abstract class KotlinSmartStepTarget(
    label: String?,
    highlightElement: PsiElement?,
    needBreakpointRequest: Boolean,
    expressionLines: Range<Int>
) : SmartStepTarget(label, highlightElement, needBreakpointRequest, expressionLines) {
    abstract fun createMethodFilter(): MethodFilter
}
