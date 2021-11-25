@file:Suppress("unused")

package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment

private val logger = Logger.getInstance("#${PluginEnvironment.PLUGIN_ID}")

internal fun logError(contextName: String? = null, messageProvider: () -> String) {
    logError(traceInfo = null, contextName = contextName, messageProvider = messageProvider)
}

internal fun logError(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logError(traceInfo = null, contextName = contextName, throwable = throwable, messageProvider = messageProvider)
}

internal fun logError(traceInfo: TraceInfo? = null, contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logError(buildMessageFrom(traceInfo, contextName, messageProvider), throwable)
}

internal fun logError(message: String, throwable: Throwable? = null) {
    logger.error(message, throwable)
}

internal fun logWarn(contextName: String? = null, messageProvider: () -> String) {
    logWarn(traceInfo = null, contextName = contextName, messageProvider = messageProvider)
}

internal fun logWarn(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logWarn(traceInfo = null, contextName = contextName, throwable = throwable, messageProvider = messageProvider)
}

internal fun logWarn(traceInfo: TraceInfo? = null, contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logWarn(buildMessageFrom(traceInfo, contextName, messageProvider), throwable)
}

internal fun logWarn(message: String, throwable: Throwable? = null) {
    logger.warn(message, throwable)
}

internal fun logInfo(contextName: String? = null, messageProvider: () -> String) {
    logInfo(traceInfo = null, contextName = contextName, messageProvider = messageProvider)
}

internal fun logInfo(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logInfo(traceInfo = null, contextName = contextName, throwable = throwable, messageProvider = messageProvider)
}

internal fun logInfo(traceInfo: TraceInfo? = null, contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logInfo(buildMessageFrom(traceInfo, contextName, messageProvider), throwable)
}

internal fun logInfo(message: String, throwable: Throwable? = null) {
    logger.info(message, throwable)
}

internal fun logDebug(contextName: String? = null, messageProvider: () -> String) {
    logDebug(traceInfo = null, contextName = contextName, messageProvider = messageProvider)
}

internal fun logDebug(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logDebug(traceInfo = null, contextName = contextName, throwable = throwable, messageProvider = messageProvider)
}

internal fun logDebug(traceInfo: TraceInfo? = null, contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logDebug(buildMessageFrom(traceInfo, contextName, messageProvider), throwable)
}

internal fun logDebug(message: String, throwable: Throwable? = null) {
    if (!FeatureFlags.useDebugLogging) return
    if (!logger.isDebugEnabled) warnNotLoggable()
    logger.debug(message, throwable)
}

internal fun logTrace(contextName: String? = null, messageProvider: () -> String) {
    logTrace(traceInfo = null, contextName = contextName, messageProvider = messageProvider)
}

internal fun logTrace(traceInfo: TraceInfo? = null, contextName: String? = null, messageProvider: () -> String) {
    logTrace(buildMessageFrom(traceInfo, contextName, messageProvider))
}

private inline fun catchAndSuppress(action: () -> Unit) {
    try {
        action()
    } catch (e: Throwable) {
    }
}

internal fun logTrace(message: String) = catchAndSuppress {
    if (!FeatureFlags.useDebugLogging) return
    if (!logger.isTraceEnabled) warnNotLoggable()

    logger.trace(message)
}

internal fun logTrace(throwable: Throwable) = catchAndSuppress {
    if (!FeatureFlags.useDebugLogging) return
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
    messageProvider: () -> String
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

    append(messageProvider())
}
