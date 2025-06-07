// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.coroutine.data

import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.SuspendContext
import com.sun.jdi.ObjectReference
import com.sun.jdi.ThreadReference
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.MirrorOfCoroutineInfo

@ApiStatus.Internal
data class CoroutineStacksInfoData(
    val continuationStackFrames: List<CoroutineStackFrameItem>,
    val creationStackFrames: List<CreationCoroutineStackFrameItem>,
) {
    val topFrameVariables: List<JavaValue> by lazy {
        continuationStackFrames.firstOrNull()?.spilledVariables ?: emptyList()
    }
}

open class CoroutineInfoData(
    name: String?,
    val id: Long?,
    state: String?,
    dispatcher: String?,
    val lastObservedFrame: ObjectReference?,
    val lastObservedThread: ThreadReference?,
    val debugCoroutineInfoRef: ObjectReference?,
    private val stackFrameProvider: CoroutineStackFramesProvider?
) {
    val name: String = name ?: DEFAULT_COROUTINE_NAME

    val state: State = State.fromString(state)

    var job: String? = null

    var parentJob: String? = null

    // NOTE: dispatchers may have a custom String representation, see IDEA-371498
    val dispatcher: String? by lazy {
        dispatcher?.let {
            CLASS_WITH_HASHCODE_REGEX.matchEntire(it)?.groups[1]?.value ?: it
        }
    }

    private val contextSummary by lazy {
        "[${this.dispatcher}${if (job == null) "" else ", $job"}]"
    }

    val coroutineDescriptor: String by lazy {
        "\"${this.name}:$id\" $state ${if (isRunning) "on thread ${lastObservedThread?.name() ?: UNKNOWN_THREAD }" else "" } $contextSummary"
    }

    private val coroutineStackFrames: CoroutineStacksInfoData? by lazy {
        stackFrameProvider?.fetchCoroutineStackFrames(lastObservedFrame)
    }

    open val continuationStackFrames: List<CoroutineStackFrameItem> by lazy {
        coroutineStackFrames?.continuationStackFrames ?: emptyList()
    }

    open val creationStackFrames: List<CreationCoroutineStackFrameItem> by lazy {
        coroutineStackFrames?.creationStackFrames ?: emptyList()
    }

    val isSuspended: Boolean = this.state == State.SUSPENDED

    val isRunning: Boolean = this.state == State.RUNNING

    val isCreated: Boolean = this.state == State.CREATED
    
    fun isRunningOnCurrentThread(suspendContext: SuspendContext): Boolean =
        lastObservedThread == suspendContext.thread?.threadReference

    companion object {
        @Deprecated("This API will not be exposed in the future versions.")
        const val DEFAULT_COROUTINE_NAME: String = "coroutine"
        @Deprecated("This API will not be exposed in the future versions.")
        const val DEFAULT_COROUTINE_STATE: String = "UNKNOWN"
        internal const val UNKNOWN_JOB: String = "UNKNOWN_JOB"
        private const val UNKNOWN_THREAD: String = "UNKNOWN_THREAD"
        private val CLASS_WITH_HASHCODE_REGEX = "([a-zA-Z0-9._]+@[0-9a-f]+)(.*?)?".toRegex()
    }

    @Deprecated("Please use API of CoroutineInfoData instead.")
    val descriptor: CoroutineDescriptor by lazy {
        CoroutineDescriptor(
            name = this.name,
            id = id.toString(),
            state = this.state,
            dispatcher = dispatcher,
            contextSummary = contextSummary
        )
    }

    @Deprecated("Please use lastObservedThread instead.", ReplaceWith("lastObservedThread"))
    val activeThread: ThreadReference? by lazy { lastObservedThread }

    @Deprecated("The hierarchy of parent jobs for a current coroutine is not computed anymore.")
    val jobHierarchy: List<String> by lazy { emptyList() }
}

@ApiStatus.Internal
fun createCoroutineInfoDataFromMirror(
    mirror: MirrorOfCoroutineInfo,
    stackFrameProvider: CoroutineStackFramesProvider
): CoroutineInfoData =
    CoroutineInfoData(
        name = mirror.context?.name,
        id = mirror.sequenceNumber,
        state = mirror.state,
        dispatcher = mirror.context?.dispatcher,
        lastObservedFrame = mirror.lastObservedFrame,
        lastObservedThread = mirror.lastObservedThread,
        debugCoroutineInfoRef = null,
        stackFrameProvider = stackFrameProvider
    )

@ApiStatus.Internal
enum class State(val state: String) {
    RUNNING("RUNNING"),
    SUSPENDED("SUSPENDED"),
    CREATED("CREATED"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromString(state: String?): State {
            return entries.find { it.state.equals(state, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

@Deprecated("Please use CoroutineInfoData API instead.")
class CompleteCoroutineInfoData(
    descriptor: CoroutineDescriptor,
    continuationStackFrames: List<CoroutineStackFrameItem>,
    creationStackFrames: List<CreationCoroutineStackFrameItem>,
    activeThread: ThreadReference? = null, // for suspended coroutines should be null
    jobHierarchy: List<String> = emptyList()
) : CoroutineInfoData(
    name = descriptor.name,
    id = descriptor.id.toLong(),
    state = descriptor.state.state,
    dispatcher = descriptor.dispatcher,
    lastObservedFrame = null,
    lastObservedThread = activeThread,
    debugCoroutineInfoRef = null,
    stackFrameProvider = null
)

@Deprecated("Please use CoroutineInfoData API instead.")
fun CoroutineInfoData.toCompleteCoroutineInfoData() =
    when (this) {
        is CompleteCoroutineInfoData -> this
        else ->
            CompleteCoroutineInfoData(
                descriptor,
                continuationStackFrames,
                creationStackFrames,
                activeThread,
                jobHierarchy
            )
    }

@Deprecated("Please use CoroutineInfoData API instead.")
data class CoroutineDescriptor(val name: String, val id: String, val state: State, val dispatcher: String?, val contextSummary: String?) {
    fun formatName() = "$name:$id"

    companion object {
        fun instance(mirror: MirrorOfCoroutineInfo): CoroutineDescriptor =
            CoroutineDescriptor(
                mirror.context?.name ?: CoroutineInfoData.DEFAULT_COROUTINE_NAME,
                "${mirror.sequenceNumber}",
                State.valueOf(mirror.state ?: CoroutineInfoData.DEFAULT_COROUTINE_STATE),
                mirror.context?.dispatcher,
                mirror.context?.summary
            )
    }
}
