// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.coroutine.data

import com.sun.jdi.ObjectReference
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.LocationCache
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.DebugMetadata
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.MirrorOfBaseContinuationImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.MirrorOfCoroutineInfo
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.spilledValues
import org.jetbrains.kotlin.idea.debugger.coroutine.util.isCreationSeparatorFrame
import org.jetbrains.kotlin.idea.debugger.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.invokeInManagerThread

class CoroutineStackTraceProvider(private val executionContext: DefaultExecutionContext) {
    private val locationCache = LocationCache(executionContext)
    private val debugMetadata: DebugMetadata? = DebugMetadata.instance(executionContext)

    data class CoroutineStackFrames(
        val restoredStackFrames: List<SuspendCoroutineStackFrameItem>,
        val creationStackFrames: List<CreationCoroutineStackFrameItem>
    )

    fun findStackFrames(mirror: MirrorOfCoroutineInfo): CoroutineStackFrames? {
        return executionContext.debugProcess.invokeInManagerThread {
            val frames = mirror.enhancedStackTraceProvider
                .getStackTrace()
                ?.map { it.stackTraceElement() }
                ?: return@invokeInManagerThread null
            val baseContinuationList = getAllBaseContinuationImpls(mirror.lastObservedFrame)

            val index = frames.indexOfFirst { it.isCreationSeparatorFrame() }
            val restoredStackTraceElements = if (index >= 0)
                frames.take(index)
            else
                frames

            val restoredStackFrames = restoredStackTraceElements.mapIndexed { ix, element ->
                val variables = baseContinuationList.getOrNull(ix)?.spilledValues(executionContext) ?: emptyList()
                SuspendCoroutineStackFrameItem(element, locationCache.createLocation(element), variables)
            }
            val creationStackFrames = frames.subList(index + 1, frames.size).mapIndexed { ix, element ->
                CreationCoroutineStackFrameItem(element, locationCache.createLocation(element), ix == 0)
            }

            return@invokeInManagerThread CoroutineStackFrames(restoredStackFrames, creationStackFrames)
        }
    }

    /**
     * Restores array of BaseContinuationImpl's for each restored frame based on the CoroutineInfo's last frame.
     * Start from 'lastObservedFrame' and following 'completion' property until the end of the chain (completion = null).
     */
    private fun getAllBaseContinuationImpls(lastObservedFrame: ObjectReference?): List<MirrorOfBaseContinuationImpl> {
        val restoredBaseContinuationImpl = mutableListOf<MirrorOfBaseContinuationImpl>()
        var observedFrame = lastObservedFrame
        while (observedFrame != null) {
            val baseContinuationImpl = debugMetadata?.baseContinuationImpl?.mirror(observedFrame, executionContext)
            if (baseContinuationImpl != null) {
                restoredBaseContinuationImpl.add(baseContinuationImpl)
                observedFrame = baseContinuationImpl.nextContinuation
            } else
                break
        }
        return restoredBaseContinuationImpl
    }
}
