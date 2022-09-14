// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.coroutine.data

import com.intellij.debugger.engine.JavaValue
import com.sun.jdi.ThreadReference
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData.Companion.DEFAULT_COROUTINE_NAME
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData.Companion.DEFAULT_COROUTINE_STATE
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.MirrorOfCoroutineInfo

abstract class CoroutineInfoData(val descriptor: CoroutineDescriptor) {
    abstract val stackTrace: List<CoroutineStackFrameItem>
    abstract val creationStackTrace: List<CreationCoroutineStackFrameItem>
    abstract val activeThread: ThreadReference?

    val topFrameVariables: List<JavaValue> by lazy {
        stackTrace.firstOrNull()?.spilledVariables ?: emptyList()
    }

    fun isSuspended() = descriptor.state == State.SUSPENDED

    fun isCreated() = descriptor.state == State.CREATED

    fun isRunning() = descriptor.state == State.RUNNING

    companion object {
        const val DEFAULT_COROUTINE_NAME = "coroutine"
        const val DEFAULT_COROUTINE_STATE = "UNKNOWN"
    }
}

class LazyCoroutineInfoData(
    private val mirror: MirrorOfCoroutineInfo,
    private val stackTraceProvider: CoroutineStackTraceProvider
) : CoroutineInfoData(CoroutineDescriptor.instance(mirror)) {
    private val stackFrames: CoroutineStackTraceProvider.CoroutineStackFrames? by lazy {
        stackTraceProvider.findStackFrames(mirror)
    }

    override val stackTrace by lazy {
        stackFrames?.restoredStackFrames ?: emptyList()
    }

    override val creationStackTrace by lazy {
        stackFrames?.creationStackFrames ?: emptyList()
    }

    override val activeThread = mirror.lastObservedThread
}

class CompleteCoroutineInfoData(
    descriptor: CoroutineDescriptor,
    override val stackTrace: List<CoroutineStackFrameItem>,
    override val creationStackTrace: List<CreationCoroutineStackFrameItem>,
    override val activeThread: ThreadReference? = null, // for suspended coroutines should be null
) : CoroutineInfoData(descriptor)

fun CoroutineInfoData.toCompleteCoroutineInfoData() =
    when (this) {
        is CompleteCoroutineInfoData -> this
        else ->
            CompleteCoroutineInfoData(
                descriptor,
                stackTrace,
                creationStackTrace,
                activeThread
            )
    }

data class CoroutineDescriptor(val name: String, val id: String, val state: State, val dispatcher: String?) {
    fun formatName() =
        "$name:$id"

    companion object {
        fun instance(mirror: MirrorOfCoroutineInfo): CoroutineDescriptor =
            CoroutineDescriptor(
                mirror.context?.name ?: DEFAULT_COROUTINE_NAME,
                "${mirror.sequenceNumber}",
                State.valueOf(mirror.state ?: DEFAULT_COROUTINE_STATE),
                mirror.context?.dispatcher
            )
    }
}

enum class State {
    RUNNING,
    SUSPENDED,
    CREATED,
    UNKNOWN,
    SUSPENDED_COMPLETING,
    SUSPENDED_CANCELLING,
    CANCELLED,
    COMPLETED,
    NEW
}
