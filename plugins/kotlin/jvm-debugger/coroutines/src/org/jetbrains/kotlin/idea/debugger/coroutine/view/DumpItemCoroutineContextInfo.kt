// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine.view

import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData

/**
 * Additional info about [CoroutineDumpItem] holding raw [kotlinx.coroutines.CoroutineDispatcher] and [kotlinx.coroutines.Job]
 */
internal data class DumpItemCoroutineContextInfo(
    val dispatcher: String?,
    val job: String?,
) {
    companion object {
        /**
         * Builds dump-item context info from live debugger data.
         */
        fun from(info: CoroutineInfoData): DumpItemCoroutineContextInfo? {
            if (info.dispatcher == null && info.job == null) {
                return null
            }
            return DumpItemCoroutineContextInfo(info.dispatcher, info.job)
        }

        /**
         * Parses the serialized dump-item fragment, for example
         * `"[dispatcher=Dispatchers.Default, job=StandaloneCoroutine{Active}]"`.
         */
        fun parse(serialized: String?): DumpItemCoroutineContextInfo? {
            val rawInfo = serialized?.trim() ?: return DumpItemCoroutineContextInfo(null, null)
            if (rawInfo.firstOrNull() != '[' || rawInfo.lastOrNull() != ']') {
                return DumpItemCoroutineContextInfo(null, null)
            }

            var dispatcher: String? = null
            var job: String? = null
            for (entry in rawInfo.removePrefix("[").removeSuffix("]").split(", ")) {
                when {
                    entry.startsWith("dispatcher=") -> dispatcher = entry.removePrefix("dispatcher=")
                    entry.startsWith("job=") -> job = entry.removePrefix("job=")
                }
            }
            if (dispatcher == null && job == null) {
                return null
            }
            return DumpItemCoroutineContextInfo(dispatcher, job)
        }
    }
}

/**
 * Serializes this context info back into the dump-item header format.
 * Example: "[dispatcher=Dispatchers.Default, job=StandaloneCoroutine{Active}]"
 */
internal fun DumpItemCoroutineContextInfo.serialize(): String {
    return buildList {
        dispatcher?.let { add("dispatcher=$it") }
        job?.let { add("job=$it") }
    }.joinToString(prefix = "[", separator = ", ", postfix = "]")
}