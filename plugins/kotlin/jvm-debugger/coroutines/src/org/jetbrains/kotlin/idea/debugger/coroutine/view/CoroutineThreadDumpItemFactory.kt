// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine.view

import com.intellij.threadDumpParser.ThreadState
import com.intellij.unscramble.MergeableDumpItem
import com.intellij.unscramble.ThreadDumpItemFactory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.debugger.coroutine.data.State

private val coroutineHeaderPattern = Regex(
    """
    ^"[^"]+"
    \s[^\r\n]+\s(?<coroutineState>[^\s\[]+)\s\Q[Coroutine]\E
    (?:\s(?<coroutineContext>\[[^\r\n]*]))?
    $
    """.trimIndent(),
    setOf(RegexOption.IGNORE_CASE, RegexOption.COMMENTS),
)

@ApiStatus.Internal
internal class CoroutineThreadDumpItemFactory : ThreadDumpItemFactory {
    override fun createDumpItem(threadState: ThreadState): MergeableDumpItem? = createCoroutineDumpItem(threadState)
}

private fun createCoroutineDumpItem(threadState: ThreadState): CoroutineDumpItem? {
    val stackTrace = threadState.stackTrace ?: return null
    val header = stackTrace.lineSequence().firstOrNull() ?: return null
    val headerMatch = coroutineHeaderPattern.matchEntire(header) ?: return null
    val coroutineState = headerMatch.groups["coroutineState"]?.value ?: return null
    return CoroutineDumpItem(
        name = restoreCoroutineName(threadState),
        treeId = threadState.uniqueId,
        parentTreeId = threadState.threadContainerUniqueId,
        coroutineState = State.fromString(coroutineState),
        coroutineContext = headerMatch.groups["coroutineContext"]?.value,
        stackTrace = stackTrace,
    )
}

private fun restoreCoroutineName(threadState: ThreadState): String {
    val uniqueId = threadState.uniqueId ?: return threadState.name
    return threadState.name.removeSuffix("@$uniqueId")
}
