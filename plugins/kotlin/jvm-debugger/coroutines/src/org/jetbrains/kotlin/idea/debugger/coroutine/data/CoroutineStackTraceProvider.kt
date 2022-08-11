// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.coroutine.data

import com.intellij.debugger.engine.JavaValue
import com.sun.jdi.ObjectReference
import org.jetbrains.kotlin.idea.debugger.core.invokeInManagerThread
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.LocationCache
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.DebugMetadata
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.FieldVariable
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.MirrorOfCoroutineInfo
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.toJavaValue
import org.jetbrains.kotlin.idea.debugger.coroutine.util.isCreationSeparatorFrame
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext

class CoroutineStackTraceProvider(private val executionContext: DefaultExecutionContext) {
    companion object {
        val METHOD_PREFIXES_TO_SKIP = arrayOf("getStackTrace", "enhanceStackTraceWithThreadDump")
    }

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
                ?.dropWhile { frame ->
                    METHOD_PREFIXES_TO_SKIP.any { frame.methodName.contains(it) }
                }
                ?.map { it.stackTraceElement() }
                ?: return@invokeInManagerThread null

            val index = frames.indexOfFirst { it.isCreationSeparatorFrame() }
            val restoredStackTraceElements = if (index >= 0)
                frames.take(index)
            else
                frames

            val spilledVariablesPerFrame = getSpilledVariablesForNFrames(mirror.lastObservedFrame, restoredStackTraceElements.size)
            val restoredStackFrames = restoredStackTraceElements.mapIndexed { ix, element ->
                val variables = spilledVariablesPerFrame.getOrNull(ix) ?: emptyList()
                SuspendCoroutineStackFrameItem(element, locationCache.createLocation(element), variables)
            }
            val creationStackFrames = frames.subList(index + 1, frames.size).mapIndexed { ix, element ->
                CreationCoroutineStackFrameItem(element, locationCache.createLocation(element), ix == 0)
            }

            return@invokeInManagerThread CoroutineStackFrames(restoredStackFrames, creationStackFrames)
        }
    }

    private fun getSpilledVariablesForNFrames(lastObservedFrame: ObjectReference?, n: Int): List<List<JavaValue>> {
        val baseContinuationImpl = debugMetadata?.baseContinuationImpl ?: return emptyList()
        val spilledVariablesPerFrame = mutableListOf<List<JavaValue>>()
        var observedFrame = lastObservedFrame
        while (observedFrame != null && spilledVariablesPerFrame.size < n) {
            val spilledVariables = debugMetadata.baseContinuationImpl.getSpilledVariableFieldMapping(observedFrame, executionContext)
            spilledVariablesPerFrame.add(spilledVariables.toJavaValues(observedFrame))
            observedFrame = baseContinuationImpl.getNextContinuation(observedFrame, executionContext)
        }
        return spilledVariablesPerFrame
    }

    private fun List<FieldVariable>.toJavaValues(continuation: ObjectReference) =
        map {
            it.toJavaValue(continuation, executionContext)
        }
}
