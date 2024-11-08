// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.google.gson.Gson
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.sun.jdi.*
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.data.*
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.*
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class CoroutinesInfoFromJsonAndReferencesProvider(
  private val executionContext: DefaultExecutionContext,
  private val debugProbesImpl: DebugProbesImpl
) : CoroutineInfoProvider {
    private val stackFramesProvider = CoroutineStackFramesProvider(executionContext)
    private val jobHierarchyProvider = CoroutineJobHierarchyProvider()
    
    override fun dumpCoroutinesInfo(): List<CoroutineInfoData> {
        val array = debugProbesImpl.dumpCoroutinesInfoAsJsonAndReferences(executionContext)
            ?: return emptyList()

        val arrayValues = array.values // fetch all values at once

        if (arrayValues.size != 4) {
            error("The result array of 'dumpCoroutinesInfoAsJSONAndReferences' should be of size 4")
        }

        val coroutinesInfoAsJsonString = arrayValues[0].safeAs<StringReference>()?.value()
            ?: error("The first element of the result array must be a string")
        val lastObservedThreadRefs = arrayValues[1].safeAs<ArrayReference>()?.toTypedList<ThreadReference?>()
            ?: error("The second element of the result array must be an array")
        val lastObservedFrameRefs = arrayValues[2].safeAs<ArrayReference>()?.toTypedList<ObjectReference?>()
            ?: error("The third element of the result array must be an array")
        val coroutineInfoRefs = arrayValues[3].safeAs<ArrayReference>()?.toTypedList<ObjectReference>()
            ?: error("The fourth element of the result array must be an array")
        val coroutinesInfo = Gson().fromJson(coroutinesInfoAsJsonString, Array<CoroutineInfoFromJson>::class.java)

        if (coroutineInfoRefs.size != lastObservedFrameRefs.size ||
            lastObservedFrameRefs.size != coroutinesInfo.size ||
            coroutinesInfo.size != lastObservedThreadRefs.size) {
            error("Arrays must have equal sizes")
        }

        return calculateCoroutineInfoData(coroutinesInfo, coroutineInfoRefs, lastObservedThreadRefs, lastObservedFrameRefs)
    }

    private fun calculateCoroutineInfoData(
      coroutineInfos: Array<CoroutineInfoFromJson>,
      coroutineInfoRefs: List<ObjectReference>,
      lastObservedThreadRefs: List<ThreadReference?>,
      lastObservedFrameRefs: List<ObjectReference?>
    ): List<CoroutineInfoData> {
        val result = mutableListOf<LazyCoroutineInfoData>()
        for ((i, info) in coroutineInfos.withIndex()) {
            result.add(
                getLazyCoroutineInfoData(
                    info,
                    coroutineInfoRefs[i],
                    lastObservedThreadRefs[i],
                    lastObservedFrameRefs[i],
                    stackFramesProvider,
                    jobHierarchyProvider
                )
            )
        }
        return result
    }

    private fun getLazyCoroutineInfoData(
      info: CoroutineInfoFromJson,
      coroutineInfoRef: ObjectReference,
      lastObservedThreadRef: ThreadReference?,
      lastObservedFrameRef: ObjectReference?,
      stackTraceProvider: CoroutineStackFramesProvider,
      jobHierarchyProvider: CoroutineJobHierarchyProvider
    ): LazyCoroutineInfoData {
      DebuggerManagerThreadImpl.assertIsManagerThread()

        // coroutineInfo is a DebugCoroutineInfo. Need to get coroutineInfoRef.context to pass in to CoroutineContext
        val contextRef = CoroutineInfo.instance(executionContext)?.getContextRef(coroutineInfoRef)
        val coroutineContextMirror = contextRef?.let {
            CoroutineContext(executionContext).fetchMirror(info.name, info.id, info.dispatcher, it, executionContext)
        } ?: MirrorOfCoroutineContext(
          info.name,
          info.id,
          info.dispatcher,
          null,
          null
        )
        val coroutineInfoMirror = debugProbesImpl.getCoroutineInfo(
            coroutineInfoRef,
            executionContext,
            coroutineContextMirror,
            info.sequenceNumber,
            info.state,
            lastObservedThreadRef,
            lastObservedFrameRef
        )

        return LazyCoroutineInfoData(coroutineInfoMirror, stackTraceProvider, jobHierarchyProvider)
    }

    private data class CoroutineInfoFromJson(
        val name: String?,
        val id: Long?,
        val dispatcher: String?,
        val sequenceNumber: Long?,
        val state: String?
    )

    private inline fun <reified T> ArrayReference.toTypedList(): List<T> {
        return values.map { it as? T ?: error("Value has type ${it::class.java}, but ${T::class.java} was expected") }
    }

    companion object {
        fun instance(executionContext: DefaultExecutionContext, debugProbesImpl: DebugProbesImpl): CoroutinesInfoFromJsonAndReferencesProvider? {
            if (debugProbesImpl.canDumpCoroutinesInfoAsJsonAndReferences()) {
                return CoroutinesInfoFromJsonAndReferencesProvider(executionContext, debugProbesImpl)
            }
            return null
        }

        val log by logger
    }
}
