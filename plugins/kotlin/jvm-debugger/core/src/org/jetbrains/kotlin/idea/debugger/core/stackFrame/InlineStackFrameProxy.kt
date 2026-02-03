// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.stackFrame

import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.sun.jdi.Location
import com.sun.jdi.StackFrame
import org.jetbrains.kotlin.idea.debugger.base.util.safeStackFrame
import org.jetbrains.kotlin.idea.debugger.base.util.safeThreadProxy

class InlineStackFrameProxyImpl(
    private val location: Location?,
    val inlineDepth: Int, // This variable is a helper for evaluator
    threadProxy: ThreadReferenceProxyImpl,
    stackFrame: StackFrame,
    indexFromBottom: Int,
    val inlineScopeNumber: Int,
    val surroundingScopeNumber: Int
) : StackFrameProxyImpl(threadProxy, stackFrame, indexFromBottom) {
    override fun location(): Location? = location
}

fun safeInlineStackFrameProxy(
    location: Location?,
    inlineDepth: Int,
    frameProxy: StackFrameProxyImpl,
    inlineScopeNumber: Int,
    surroundingScopeNumber: Int
): StackFrameProxyImpl {
    val threadProxy = frameProxy.safeThreadProxy() ?: return frameProxy
    val stackFrame = frameProxy.safeStackFrame() ?: return frameProxy
    return InlineStackFrameProxyImpl(
        location,
        inlineDepth,
        threadProxy,
        stackFrame,
        frameProxy.indexFromBottom,
        inlineScopeNumber,
        surroundingScopeNumber
    )
}
