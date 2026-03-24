// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine.view

import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.data.State.RUNNING

/**
 * Additional info about [CoroutineDumpItem]
 *
 * @param dispatcher raw [kotlinx.coroutines.CoroutineDispatcher] where the coroutine is running
 * @param job raw [kotlinx.coroutines.Job] of the coroutine
 * @param runningThread thread name of the [Thread] where coroutine is running. Available only in the [RUNNING] state.
 */
internal data class DumpItemCoroutineContextInfo(
    val dispatcher: String?,
    val job: String?,
    val runningThread: String?,
) {
    companion object {
        /**
         * Builds dump-item context info from live debugger data.
         */
        fun from(info: CoroutineInfoData): DumpItemCoroutineContextInfo? {
            if (info.dispatcher == null && info.job == null && info.runningThread == null) {
                return null
            }
            return DumpItemCoroutineContextInfo(info.dispatcher, info.job, info.runningThread)
        }

        /**
         * Parses the serialized dump-item fragment, for example
         * `"[dispatcher=Dispatchers.Default, job=StandaloneCoroutine{Active}]"`.
         */
        fun parse(serialized: String?): DumpItemCoroutineContextInfo? {
            val rawInfo = serialized?.trim() ?: return null
            if (rawInfo.firstOrNull() != '[' || rawInfo.lastOrNull() != ']') {
                return null
            }

            var dispatcher: String? = null
            var job: String? = null
            var runningThread: String? = null
            // TODO: if dispatcher/job/runningThread will have , in the name, we will parse it in a wrong way
            //   so we need to escape ',' during serialization
            for (entry in rawInfo.removePrefix("[").removeSuffix("]").split(", ")) {
                when {
                    entry.startsWith("dispatcher=") -> dispatcher = entry.removePrefix("dispatcher=")
                    entry.startsWith("job=") -> job = entry.removePrefix("job=")
                    entry.startsWith("runningThread=") -> runningThread = entry.removePrefix("runningThread=")
                }
            }
            if (dispatcher == null && job == null && runningThread == null) {
                return null
            }
            return DumpItemCoroutineContextInfo(dispatcher, job, runningThread)
        }
    }
}

internal fun DumpItemCoroutineContextInfo.presentableString(): String {
    return buildList {
        dispatcher?.let { add(it) }
        job?.let { add(it) }
    }.joinToString(prefix = "[", separator = ", ", postfix = "]")
}

/**
 * Serializes this context info back into the dump-item header format.
 * Example: "[dispatcher=Dispatchers.Default, job=StandaloneCoroutine{Active}, runningThread=DefaultDispatcher-worker-1]"
 */
internal fun DumpItemCoroutineContextInfo.serialize(): String {
    return buildList {
        dispatcher?.let { add("dispatcher=$it") }
        job?.let { add("job=$it") }
        runningThread?.let { add("runningThread=$it") }
    }.joinToString(prefix = "[", separator = ", ", postfix = "]")
}