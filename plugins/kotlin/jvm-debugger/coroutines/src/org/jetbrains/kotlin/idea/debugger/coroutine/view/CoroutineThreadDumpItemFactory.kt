// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine.view

import com.intellij.threadDumpParser.ThreadState
import com.intellij.unscramble.CompoundDumpItem
import com.intellij.unscramble.DumpItem
import com.intellij.unscramble.IntelliJThreadDumpMetadata
import com.intellij.unscramble.ThreadDumpItemFactory
import com.intellij.unscramble.splitFirstLineAndBody
import org.jetbrains.kotlin.idea.debugger.coroutine.data.State

internal class CoroutineThreadDumpItemFactory : ThreadDumpItemFactory {
    override fun createDumpItem(threadState: ThreadState): DumpItem? = when (threadState.type) {
        IntelliJThreadDumpMetadata.COROUTINE_TYPE -> createCoroutineDumpItem(threadState)
        IntelliJThreadDumpMetadata.COROUTINE_ROOT_TYPE -> CoroutineRootDumpItem
        else -> null
    }
}

private fun createCoroutineDumpItem(threadState: ThreadState): DumpItem? {
    if (threadState.type != IntelliJThreadDumpMetadata.COROUTINE_TYPE) {
        return null
    }
    val stackTrace = threadState.stackTrace?.trimEnd() ?: return null
    val stackTraceBody = splitFirstLineAndBody(stackTrace).body
    val item = CoroutineDumpItem(
        name = restoreCoroutineName(threadState),
        treeId = threadState.uniqueId,
        parentTreeId = threadState.threadContainerUniqueId ?: CoroutineRootDumpItem.treeId,
        coroutineState = State.fromString(threadState.state),
        coroutineContextInfo = DumpItemCoroutineContextInfo.fromMetadata(threadState.metadata),
        stackTraceBody = stackTraceBody
    )
    return if (threadState.similarThreadsCount > 1) {
        CompoundDumpItem(item, threadState.similarThreadsCount)
    } else {
        item
    }
}

private fun restoreCoroutineName(threadState: ThreadState): String {
    val uniqueId = threadState.uniqueId ?: return threadState.name
    return threadState.name.removeSuffix("@$uniqueId")
}
