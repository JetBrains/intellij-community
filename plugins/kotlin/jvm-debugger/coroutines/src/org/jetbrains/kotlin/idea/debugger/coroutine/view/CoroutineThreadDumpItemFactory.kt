// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine.view

import com.intellij.threadDumpParser.ThreadState
import com.intellij.unscramble.MergeableDumpItem
import com.intellij.unscramble.ThreadDumpItemFactory
import com.intellij.unscramble.splitFirstLineAndBody
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.debugger.coroutine.data.State

internal const val COROUTINE_THREAD_DUMP_TYPE: String = "coroutine"

@ApiStatus.Internal
internal class CoroutineThreadDumpItemFactory : ThreadDumpItemFactory {
    override fun createDumpItem(threadState: ThreadState): MergeableDumpItem? = createCoroutineDumpItem(threadState)
}

private fun createCoroutineDumpItem(threadState: ThreadState): CoroutineDumpItem? {
    if (threadState.type != COROUTINE_THREAD_DUMP_TYPE) {
        return null
    }
    val stackTrace = threadState.stackTrace?.trimEnd() ?: return null
    val stackTraceBody = splitFirstLineAndBody(stackTrace).body
    return CoroutineDumpItem(
        name = restoreCoroutineName(threadState),
        treeId = threadState.uniqueId,
        parentTreeId = threadState.threadContainerUniqueId,
        coroutineState = State.fromString(threadState.state),
        coroutineContextInfo = DumpItemCoroutineContextInfo.fromMetadata(threadState.metadata),
        stackTraceBody = stackTraceBody,
    )
}

private fun restoreCoroutineName(threadState: ThreadState): String {
    val uniqueId = threadState.uniqueId ?: return threadState.name
    return threadState.name.removeSuffix("@$uniqueId")
}
