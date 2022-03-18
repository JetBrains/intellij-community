// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.coroutine

import com.google.gson.Gson
import com.sun.jdi.ArrayReference
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference
import com.sun.jdi.ThreadReference
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineStackTraceProvider
import org.jetbrains.kotlin.idea.debugger.coroutine.data.LazyCoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.CoroutineInfoProvider
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.DebugProbesImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.MirrorOfCoroutineContext
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger
import org.jetbrains.kotlin.idea.debugger.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class CoroutinesInfoFromJsonAndReferencesProvider(
    private val executionContext: DefaultExecutionContext,
    private val debugProbesImpl: DebugProbesImpl
) : CoroutineInfoProvider {
    private val stackTraceProvider = CoroutineStackTraceProvider(executionContext)

    override fun dumpCoroutinesInfo(): List<CoroutineInfoData> {
        val array = debugProbesImpl.dumpCoroutinesInfoAsJsonAndReferences(executionContext)
            ?: return emptyList()

        if (array.length() != 4) {
            error("The result array of 'dumpCoroutinesInfoAsJSONAndReferences' should be of size 4")
        }

        val coroutinesInfoAsJsonString = array.getValue(0).safeAs<StringReference>()?.value()
            ?: error("The first element of the result array must be a string")
        val lastObservedThreadRefs = array.getValue(1).safeAs<ArrayReference>()?.toTypedList<ThreadReference?>()
            ?: error("The second element of the result array must be an array")
        val lastObservedFrameRefs = array.getValue(2).safeAs<ArrayReference>()?.toTypedList<ObjectReference?>()
            ?: error("The third element of the result array must be an array")
        val coroutineInfoRefs = array.getValue(3).safeAs<ArrayReference>()?.toTypedList<ObjectReference>()
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
                    stackTraceProvider
                )
            )
        }
        return result
    }

    private fun getLazyCoroutineInfoData(
        info: CoroutineInfoFromJson,
        coroutineInfosRef: ObjectReference,
        lastObservedThreadRef: ThreadReference?,
        lastObservedFrameRef: ObjectReference?,
        stackTraceProvider: CoroutineStackTraceProvider
    ): LazyCoroutineInfoData {
        val coroutineContextMirror = MirrorOfCoroutineContext(
            info.name,
            info.id,
            info.dispatcher
        )
        val coroutineInfoMirror = debugProbesImpl.getCoroutineInfo(
            coroutineInfosRef,
            executionContext,
            coroutineContextMirror,
            info.sequenceNumber,
            info.state,
            lastObservedThreadRef,
            lastObservedFrameRef
        )

        return LazyCoroutineInfoData(coroutineInfoMirror, stackTraceProvider)
    }

    private data class CoroutineInfoFromJson(
        val name: String?,
        val id: Long?,
        val dispatcher: String?,
        val sequenceNumber: Long?,
        val state: String?
    )

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

private inline fun <reified T> ArrayReference.toTypedList(): List<T> {
    val result = mutableListOf<T>()
    for (value in values) {
        if (value !is T) {
            error("Value has type ${value::class.java}, but ${T::class.java} was expected")
        }
        result.add(value)
    }
    return result
}
