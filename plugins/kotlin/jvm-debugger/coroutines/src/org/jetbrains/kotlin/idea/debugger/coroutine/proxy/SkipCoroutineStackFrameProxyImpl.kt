/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.sun.jdi.Location
import com.sun.jdi.Value
import org.jetbrains.kotlin.idea.debugger.coroutine.data.ContinuationVariableValueDescriptorImpl

class SkipCoroutineStackFrameProxyImpl(frame: StackFrameProxyImpl) :
    StackFrameProxyImpl(frame.threadProxy(), frame.stackFrame, frame.indexFromBottom)

class CoroutineStackFrameProxyImpl(
    val location: Location,
    val spilledVariables: List<JavaValue>,
    frame: StackFrameProxyImpl
) : StackFrameProxyImpl(frame.threadProxy(), frame.stackFrame, frame.indexFromBottom) {
    fun updateSpilledVariableValue(name: String, value: Value?) {
        val descriptor = spilledVariables.find { it.name == name }?.descriptor as? ContinuationVariableValueDescriptorImpl ?: return
        descriptor.updateValue(value)
    }

    override fun location(): Location =
        location
}
