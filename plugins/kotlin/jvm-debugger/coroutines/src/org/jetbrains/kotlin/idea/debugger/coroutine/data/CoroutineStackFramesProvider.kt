// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.coroutine.data

import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.LocationCache
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.*

class CoroutineStackFramesProvider(private val executionContext: DefaultExecutionContext) {
  
    private val locationCache = LocationCache(executionContext)
    private val debugMetadata: DebugMetadata? = DebugMetadata.instance(executionContext)
    
    fun getContinuationStack(mirror: MirrorOfCoroutineInfo): List<CoroutineStackFrameItem> {
        if (debugMetadata == null || mirror.lastObservedFrame == null) return emptyList()
        return debugMetadata.fetchContinuationStack(mirror.lastObservedFrame, executionContext).mapNotNull { 
            it.toCoroutineStackFrameItem(executionContext, locationCache) 
        }
    }
    
    fun getCreationStackTrace(mirror: MirrorOfCoroutineInfo): List<CreationCoroutineStackFrameItem> =
        mirror.creationStackTraceProvider.getStackTrace().mapIndexed { index, frame ->
            CreationCoroutineStackFrameItem(locationCache.createLocation(frame.stackTraceElement()), index == 0)
        }
}
