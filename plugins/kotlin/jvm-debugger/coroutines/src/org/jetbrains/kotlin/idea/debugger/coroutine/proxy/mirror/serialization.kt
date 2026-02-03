// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror

import kotlinx.serialization.Serializable

@Serializable
internal data class StackTraceElementData(val declaringClass: String, val methodName: String, val fileName: String? = null, val lineNumber: Int = -1) {
    fun stackTraceElement() = StackTraceElement(declaringClass, methodName, fileName, lineNumber)
}

@Serializable
internal data class FieldVariableData(val fieldName: String, val variableName: String)

@Serializable
internal data class CoroutineStackFrameData(
    val stackTraceElement: StackTraceElementData? = null,
    val spilledVariables: List<FieldVariableData>,
)

@Serializable
internal data class CoroutineStackTraceData(
    val continuationFrames: List<CoroutineStackFrameData>,
    val creationStack: List<StackTraceElementData>? = null,
)
