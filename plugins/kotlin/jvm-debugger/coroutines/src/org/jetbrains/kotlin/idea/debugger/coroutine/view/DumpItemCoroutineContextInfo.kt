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
         * Restores dump-item context info from parsed inline metadata.
         */
        fun fromMetadata(metadata: Map<String, String>): DumpItemCoroutineContextInfo? {
            val dispatcher = metadata["dispatcher"]
            val job = metadata["job"]
            val runningThread = metadata["runningThread"]
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
 * Serializes this context info into inline metadata entries.
 */
internal fun DumpItemCoroutineContextInfo.toMetadata(): Map<String, String> {
    return buildMap {
        dispatcher?.let { put("dispatcher", it) }
        job?.let { put("job", it) }
        runningThread?.let { put("runningThread", it) }
    }
}