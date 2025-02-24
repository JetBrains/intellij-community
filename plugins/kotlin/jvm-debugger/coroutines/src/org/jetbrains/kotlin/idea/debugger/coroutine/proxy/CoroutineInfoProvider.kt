// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.google.gson.Gson
import com.intellij.rt.debugger.coroutines.CoroutinesDebugHelper
import com.sun.jdi.ArrayReference
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference
import com.sun.jdi.ThreadReference
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.callMethodFromHelper
import org.jetbrains.kotlin.idea.debugger.coroutine.data.*
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.*
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import kotlin.reflect.typeOf

sealed interface CoroutineInfoProvider {
    fun dumpCoroutinesInfo(): List<CoroutineInfoData>?
}

internal class CoroutinesInfoFromJsonAndReferencesProvider(
    private val executionContext: DefaultExecutionContext
) : CoroutineInfoProvider {
    private val stackFramesProvider = CoroutineStackFramesProvider(executionContext)

    override fun dumpCoroutinesInfo(): List<CoroutineInfoData>? {

        val array = callMethodFromHelper(CoroutinesDebugHelper::class.java, executionContext, "dumpCoroutinesInfoAsJsonAndReferences", emptyList())
            ?: fallbackToOldMirrorDump(executionContext)

        val arrayValues = (array as? ArrayReference)?.values ?: return null

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

    private fun fallbackToOldMirrorDump(executionContext: DefaultExecutionContext): ArrayReference? {
        val debugProbesImpl = DebugProbesImpl.instance(executionContext)
        return if (debugProbesImpl != null && debugProbesImpl.isInstalled && debugProbesImpl.canDumpCoroutinesInfoAsJsonAndReferences()) {
            debugProbesImpl.dumpCoroutinesInfoAsJsonAndReferences(executionContext)
        } else null
    }

    private fun calculateCoroutineInfoData(
        coroutineInfos: Array<CoroutineInfoFromJson>,
        coroutineInfoRefs: List<ObjectReference>,
        lastObservedThreadRefs: List<ThreadReference?>,
        lastObservedFrameRefs: List<ObjectReference?>
    ): List<CoroutineInfoData> {
        return coroutineInfoRefs.mapIndexed { i, ref ->
            val info = coroutineInfos[i]
            CoroutineInfoData(
                name = info.name,
                id = info.sequenceNumber,
                state = info.state,
                dispatcher = info.dispatcher,
                lastObservedFrame = lastObservedFrameRefs[i],
                lastObservedThread = lastObservedThreadRefs[i],
                debugCoroutineInfoRef = ref,
                stackFrameProvider = stackFramesProvider
            )
        }
    }

    private data class CoroutineInfoFromJson(
        val name: String?,
        val id: Long?,
        val dispatcher: String?,
        val sequenceNumber: Long?,
        val state: String?
    )

    companion object {
        val log by logger
    }
}

internal class CoroutineLibraryAgent2Proxy(
    private val executionContext: DefaultExecutionContext,
    private val debugProbesImpl: DebugProbesImpl
) : CoroutineInfoProvider {
    private val stackFramesProvider = CoroutineStackFramesProvider(executionContext)

    override fun dumpCoroutinesInfo(): List<CoroutineInfoData> {
        val result = debugProbesImpl.dumpCoroutinesInfo(executionContext)
        return result.map {
            createCoroutineInfoDataFromMirror(it, stackFramesProvider)
        }
    }

    companion object {
        fun instance(executionContext: DefaultExecutionContext): CoroutineLibraryAgent2Proxy? {
            val debugProbesImpl = DebugProbesImpl.instance(executionContext)
            return if (debugProbesImpl != null && debugProbesImpl.isInstalled) {
                CoroutineLibraryAgent2Proxy(executionContext, debugProbesImpl)
            } else null
        }
    }
}

private inline fun <reified T> ArrayReference.toTypedList() = values.toTypedList<T>()

@VisibleForTesting
inline fun <reified T> List<Any?>.toTypedList(): List<T> = map {
    if (it == null) {
        require(typeOf<T>().isMarkedNullable) { "Value should not be null" }
        return@map null as T
    }
    require(it is T) { "Value has type ${it::class.java}, but ${T::class.java} was expected" }
    it
}
