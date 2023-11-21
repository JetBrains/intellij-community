// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror

import com.sun.jdi.ObjectReference
import com.sun.jdi.ThreadReference

data class MirrorOfStandaloneCoroutine(
    val that: ObjectReference,
    val state: MirrorOfChildContinuation?,
    val context: MirrorOfCoroutineContext?
)

data class MirrorOfCoroutineContext(
    val name: String?,
    val id: Long?,
    val dispatcher: String?,
    val job: MirrorOfJob?,
    val summary: String?
)

data class MirrorOfCoroutineOwner(val that: ObjectReference, val coroutineInfo: MirrorOfCoroutineInfo?)

data class MirrorOfDebugProbesImpl(val that: ObjectReference, val instance: ObjectReference?, val isInstalled: Boolean?)

data class MirrorOfWeakReference(val that: ObjectReference, val reference: ObjectReference?)

data class MirrorOfCoroutineInfo(
    val context: MirrorOfCoroutineContext?,
    val sequenceNumber: Long?,
    val state: String?,
    val lastObservedThread: ThreadReference?,
    val lastObservedFrame: ObjectReference?,
    val enhancedStackTraceProvider: StackTraceMirrorProvider,
    val creationStackTraceProvider: StackTraceMirrorProvider
)

data class MirrorOfJob(
    val details: String,
    val parent: JobMirrorProvider
)

data class MirrorOfCoroutineStackFrame(
    val that: ObjectReference,
    val callerFrame: MirrorOfCoroutineStackFrame?,
    val stackTraceElement: MirrorOfStackTraceElement?
)

data class MirrorOfStackTraceElement(
    val declaringClass: String,
    val methodName: String,
    val fileName: String?,
    val lineNumber: Int?
) {
    fun stackTraceElement() =
            StackTraceElement(
                    declaringClass,
                    methodName,
                    fileName,
                    lineNumber ?: -1
            )
}

data class MirrorOfChildContinuation(
    val that: ObjectReference,
    val child: MirrorOfCancellableContinuationImpl?
)

data class MirrorOfContinuationStack(val that: ObjectReference, val coroutineStack: List<MirrorOfStackFrame>)

data class MirrorOfStackFrame(
    val that: ObjectReference,
    val baseContinuationImpl: MirrorOfBaseContinuationImpl
)

data class FieldVariable(val fieldName: String, val variableName: String)

data class MirrorOfCancellableContinuationImpl(
    val that: ObjectReference,
    val decision: Int?,
    val delegate: MirrorOfDispatchedContinuation?,
    val resumeMode: Int?,
    val submissionTime: Long?,
    val jobContext: MirrorOfCoroutineContext?
)

data class MirrorOfDispatchedContinuation(
    val that: ObjectReference,
    val continuation: ObjectReference?,
)

data class MirrorOfJavaLangAbstractCollection(val that: ObjectReference, val values: List<ObjectReference>)

data class MirrorOfBaseContinuationImplLight(
    val that: ObjectReference,
    val nextContinuation: ObjectReference?,
    val coroutineOwner: ObjectReference?
)

data class MirrorOfBaseContinuationImpl(
    val that: ObjectReference,
    val stackTraceElement: MirrorOfStackTraceElement?,
    val fieldVariables: List<FieldVariable>,
    val nextContinuation: ObjectReference?,
    val coroutineOwner: ObjectReference?
)
