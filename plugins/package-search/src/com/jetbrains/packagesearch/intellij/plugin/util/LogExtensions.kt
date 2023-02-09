/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

@file:Suppress("unused")

package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment
import kotlinx.coroutines.CancellationException

private val logger = Logger.getInstance("#${PluginEnvironment.PLUGIN_ID}")

fun logError(contextName: String? = null, messageProvider: () -> String) {
    logError(traceInfo = null, contextName = contextName, messageProvider = messageProvider)
}

fun logError(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logError(traceInfo = null, contextName = contextName, throwable = throwable, messageProvider = messageProvider)
}

fun logError(traceInfo: TraceInfo? = null, contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logError(buildMessageFrom(traceInfo, contextName, messageProvider), throwable)
}

fun logError(message: String, throwable: Throwable? = null) {
    if (isNotLoggable(throwable)) return
    logger.error(message, throwable)
}

fun logWarn(contextName: String? = null, messageProvider: () -> String) {
    logWarn(traceInfo = null, contextName = contextName, messageProvider = messageProvider)
}

fun logWarn(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logWarn(traceInfo = null, contextName = contextName, throwable = throwable, messageProvider = messageProvider)
}

fun logWarn(traceInfo: TraceInfo? = null, contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logWarn(buildMessageFrom(traceInfo, contextName, messageProvider), throwable)
}

fun logWarn(message: String, throwable: Throwable? = null) {
    if (isNotLoggable(throwable)) return
    logger.warn(message, throwable)
}

fun logInfo(contextName: String? = null, messageProvider: () -> String) {
    logInfo(traceInfo = null, contextName = contextName, messageProvider = messageProvider)
}

fun logInfo(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logInfo(traceInfo = null, contextName = contextName, throwable = throwable, messageProvider = messageProvider)
}

fun logInfo(traceInfo: TraceInfo? = null, contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logInfo(buildMessageFrom(traceInfo, contextName, messageProvider), throwable)
}

fun logInfo(message: String, throwable: Throwable? = null) {
    if (isNotLoggable(throwable)) return
    logger.info(message, throwable)
}

fun logDebug(contextName: String? = null, messageProvider: () -> String) {
    logDebug(traceInfo = null, contextName = contextName, messageProvider = messageProvider)
}

fun logDebug(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logDebug(traceInfo = null, contextName = contextName, throwable = throwable, messageProvider = messageProvider)
}

fun logDebug(contextName: String? = null, throwable: Throwable? = null, message: String? = null) {
    logDebug(traceInfo = null, contextName = contextName, throwable = throwable)
}

fun logDebug(traceInfo: TraceInfo? = null, contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logDebug(buildMessageFrom(traceInfo, contextName, messageProvider), throwable)
}

fun logDebug(traceInfo: TraceInfo? = null, contextName: String? = null, throwable: Throwable? = null, message: String? = null) {
    logDebug(buildMessageFrom(traceInfo, contextName, message = message), throwable)
}

fun logDebug(message: String, throwable: Throwable? = null) {
    if (!FeatureFlags.useDebugLogging.value || isNotLoggable(throwable)) return
    if (!logger.isDebugEnabled) warnNotLoggable()
    logger.debug(message, throwable)
}

fun logTrace(contextName: String? = null, messageProvider: () -> String) {
    logTrace(traceInfo = null, contextName = contextName, messageProvider = messageProvider)
}

fun logTrace(traceInfo: TraceInfo? = null, contextName: String? = null, messageProvider: () -> String) {
    logTrace(buildMessageFrom(traceInfo, contextName, messageProvider))
}

private inline fun catchAndSuppress(action: () -> Unit) {
    try {
        action()
    } catch (e: Throwable) {
    }
}

fun logTrace(message: String) = catchAndSuppress {
    if (!FeatureFlags.useDebugLogging.value) return
    if (!logger.isTraceEnabled) warnNotLoggable()

    logger.trace(message)
}

fun logTrace(throwable: Throwable) = catchAndSuppress {
    if (!FeatureFlags.useDebugLogging.value) return
    if (!logger.isTraceEnabled) warnNotLoggable()

    logger.trace(throwable)
}

private fun warnNotLoggable() {
    logger.warn(
        """
        |!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        |Debug logging not enabled. Make sure you have a line like this:
        |      #${PluginEnvironment.PLUGIN_ID}:trace
        |in your debug log settings (Help | Diagnostic Tools | Debug Log Settings)
        |then restart the IDE.
        |!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        |""".trimMargin()
    )
}

private fun buildMessageFrom(
    traceInfo: TraceInfo?,
    contextName: String?,
    messageProvider: (() -> String)? = null,
    message: String? = null
) = buildString {
    if (traceInfo != null) {
        append(traceInfo)
        append(' ')
    }

    if (!contextName.isNullOrBlank()) {
        append(contextName)
        append(' ')
    }

    if (isNotEmpty()) append("- ")

    messageProvider?.let { append(it()) }
    message?.let { append(it) }
}

private fun isLoggable(ex: Throwable?) = when (ex) {
    is CancellationException, is ProcessCanceledException -> false
    else -> true
}

private fun isNotLoggable(ex: Throwable?) = !isLoggable(ex)