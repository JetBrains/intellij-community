// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.coroutine.data

import com.sun.jdi.ObjectReference
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.fetchCoroutineStacksInfoData

class CoroutineStackFramesProvider(private val executionContext: DefaultExecutionContext) {

    fun fetchCoroutineStackFrames(lastObservedFrame: ObjectReference?): CoroutineStacksInfoData? {
        if (lastObservedFrame == null) return null
        return fetchCoroutineStacksInfoData(executionContext, lastObservedFrame)
    }
}
