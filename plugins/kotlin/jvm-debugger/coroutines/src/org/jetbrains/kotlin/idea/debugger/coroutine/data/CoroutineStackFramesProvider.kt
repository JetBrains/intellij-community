// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.coroutine.data

import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.ContinuationHolder
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.MirrorOfCoroutineInfo

class CoroutineStackFramesProvider(executionContext: DefaultExecutionContext) {
    private val continuationHolder = ContinuationHolder.instance(executionContext)
    
    fun getContinuationStack(mirror: MirrorOfCoroutineInfo): List<CoroutineStackFrameItem> {
        val continuation = mirror.lastObservedFrame ?: return emptyList()
        return continuationHolder.extractCoroutineStacksInfoData(continuation)?.continuationStackFrames
            ?: emptyList()
    }
    
    fun getCreationStackTrace(mirror: MirrorOfCoroutineInfo): List<CreationCoroutineStackFrameItem> =
        mirror.creationStackTraceProvider.getStackTrace().mapIndexed { index, frame ->
            CreationCoroutineStackFrameItem(continuationHolder.locationCache.createLocation(frame.stackTraceElement()), index == 0)
        }
}
