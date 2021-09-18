// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stackFrame

import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.sun.jdi.Location
import com.sun.jdi.StackFrame
import org.jetbrains.kotlin.idea.debugger.safeStackFrame
import org.jetbrains.kotlin.idea.debugger.safeThreadProxy

class InlineStackFrameProxyImpl(
    private val location: Location?,
    val inlineDepth: Int, // This variable is a helper for evaluator
    threadProxy: ThreadReferenceProxyImpl,
    stackFrame: StackFrame,
    indexFromBottom: Int
) : StackFrameProxyImpl(threadProxy, stackFrame, indexFromBottom) {
    override fun location(): Location? = location
}

fun safeInlineStackFrameProxy(
    location: Location?,
    inlineDepth: Int,
    frameProxy: StackFrameProxyImpl
): StackFrameProxyImpl {
    val threadProxy = frameProxy.safeThreadProxy() ?: return frameProxy
    val stackFrame = frameProxy.safeStackFrame() ?: return frameProxy
    return InlineStackFrameProxyImpl(
        location,
        inlineDepth,
        threadProxy,
        stackFrame,
        frameProxy.indexFromBottom
    )
}
