// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror

import com.google.gson.Gson
import com.sun.jdi.ArrayReference
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference
import org.jetbrains.kotlin.idea.debugger.coroutine.toTypedList
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object CoroutinesStackTraceInfoParser {
    fun parseCoroutineStackTraceInfo(array: ArrayReference): List<MirrorOfStackFrame> {
        if (array.length() != 4) {
            error("The array should be of size 4")
        }

        val continuationRefs = array.getValue(0).safeAs<ArrayReference>()?.toTypedList<ObjectReference>()
            ?: error("The first element of the result array must be an array")
        val stackTraceElementsAsJson = array.getValue(1).safeAs<StringReference>()?.value()
            ?: error("The second element of the result array must be a string")
        val spilledVariableFieldMappingsAsJson = array.getValue(2).safeAs<StringReference>()?.value()
            ?: error("The third element of the result array must be a string")
        val nextContinuationRefs = array.getValue(3).safeAs<ArrayReference>()?.toTypedList<ObjectReference?>()
            ?: error("The fourth element of the result array must be an array")
        val stackTraceElements = Gson().fromJson(stackTraceElementsAsJson, Array<MirrorOfStackTraceElement?>::class.java)
        val spilledVariableFieldMappings = Gson().fromJson(spilledVariableFieldMappingsAsJson, Array<Array<FieldVariable>?>::class.java)

        val size = continuationRefs.size
        if (stackTraceElements.size != size ||
            spilledVariableFieldMappings.size != size ||
            nextContinuationRefs.size != size) {
            error("Arrays must have equal sizes")
        }

        return createCoroutineStack(
            continuationRefs,
            stackTraceElements,
            spilledVariableFieldMappings,
            nextContinuationRefs
        )
    }

    private fun createCoroutineStack(
        continuationRefs: List<ObjectReference>,
        stackTraceElements: Array<MirrorOfStackTraceElement?>,
        spilledVariableFieldMappings: Array<Array<FieldVariable>?>,
        nextContinuationRefs: List<ObjectReference?>
    ): List<MirrorOfStackFrame> {
        val coroutineStack = mutableListOf<MirrorOfStackFrame>()
        for (i in continuationRefs.indices) {
            val variableMappings = spilledVariableFieldMappings[i]?.toList().orEmpty()
            coroutineStack.add(
                MirrorOfStackFrame(
                    continuationRefs[i],
                    MirrorOfBaseContinuationImpl(
                        continuationRefs[i],
                        stackTraceElements[i],
                        variableMappings,
                        nextContinuationRefs[i]
                    )
                )
            )
        }

        return coroutineStack
    }
}
