// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.stackFrame

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.jdi.LocalVariableProxyImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XValueChildrenList

/**
 * A debugger extension that allows plugins to contribute to the debugger variable view
 */
interface KotlinStackFrameValueContributor {
    fun contributeValues(
        frame: KotlinStackFrame,
        context: EvaluationContextImpl,
        variables: List<LocalVariableProxyImpl>,
    ): List<XNamedValue>

    companion object {
        @JvmStatic
        val EP: ExtensionPointName<KotlinStackFrameValueContributor> =
            ExtensionPointName.create("com.intellij.debugger.kotlinStackFrameValueContributor")
    }
}
