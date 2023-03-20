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

package org.jetbrains.idea.reposearch

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private val logger = Logger.getInstance("#${PluginEnvironment.PLUGIN_ID}")

internal fun logError(contextName: String? = null, messageProvider: () -> String) {
  logError(traceInfo = null, contextName = contextName, messageProvider = messageProvider)
}

internal fun logError(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
  logError(traceInfo = null, contextName = contextName, throwable = throwable, messageProvider = messageProvider)
}

internal fun logError(
  traceInfo: TraceInfo? = null,
  contextName: String? = null,
  throwable: Throwable? = null,
  messageProvider: () -> String
) {
  logError(buildMessageFrom(traceInfo, contextName, messageProvider), throwable)
}

internal fun logError(message: String, throwable: Throwable? = null) {
  if (isNotLoggable(throwable)) return
  logger.error(message, throwable)
}

internal fun logWarn(contextName: String? = null, messageProvider: () -> String) {
  logWarn(traceInfo = null, contextName = contextName, messageProvider = messageProvider)
}

internal fun logWarn(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
  logWarn(traceInfo = null, contextName = contextName, throwable = throwable, messageProvider = messageProvider)
}

internal fun logWarn(
  traceInfo: TraceInfo? = null,
  contextName: String? = null,
  throwable: Throwable? = null,
  messageProvider: () -> String
) {
  logWarn(buildMessageFrom(traceInfo, contextName, messageProvider), throwable)
}

internal fun logWarn(message: String, throwable: Throwable? = null) {
  if (isNotLoggable(throwable)) return
  logger.warn(message, throwable)
}

internal fun logInfo(contextName: String? = null, messageProvider: () -> String) {
  logInfo(traceInfo = null, contextName = contextName, messageProvider = messageProvider)
}

internal fun logInfo(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
  logInfo(traceInfo = null, contextName = contextName, throwable = throwable, messageProvider = messageProvider)
}

internal fun logInfo(
  traceInfo: TraceInfo? = null,
  contextName: String? = null,
  throwable: Throwable? = null,
  messageProvider: () -> String
) {
  logInfo(buildMessageFrom(traceInfo, contextName, messageProvider), throwable)
}

internal fun logInfo(message: String, throwable: Throwable? = null) {
  if (isNotLoggable(throwable)) return
  logger.info(message, throwable)
}

internal fun logDebug(contextName: String? = null, messageProvider: () -> String) {
  logDebug(traceInfo = null, contextName = contextName, messageProvider = messageProvider)
}

internal fun logDebug(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
  logDebug(traceInfo = null, contextName = contextName, throwable = throwable, messageProvider = messageProvider)
}

internal fun logDebug(
  traceInfo: TraceInfo? = null,
  contextName: String? = null,
  throwable: Throwable? = null,
  messageProvider: () -> String
) {
  logDebug(buildMessageFrom(traceInfo, contextName, messageProvider), throwable)
}

internal fun logDebug(message: String, throwable: Throwable? = null) {
  if (!Registry.`is`("org.jetbrains.idea.reposearch.log.debug", false)) return
  if (!logger.isDebugEnabled && !notificationShown.getAndSet(true)) warnNotLoggable()
  logger.debug(message, throwable)
}

internal fun logTrace(contextName: String? = null, messageProvider: () -> String) {
  logTrace(traceInfo = null, contextName = contextName, messageProvider = messageProvider)
}

fun logTrace(traceInfo: TraceInfo? = null, contextName: String? = null, messageProvider: () -> String) {
  logTrace(buildMessageFrom(traceInfo, contextName, messageProvider))
}

private inline fun catchAndSuppress(action: () -> Unit) {
  runCatching { action() }
}

internal fun logTrace(message: String) = catchAndSuppress {
  if (!Registry.`is`("org.jetbrains.idea.reposearch.log.debug", false)) return
  if (!logger.isDebugEnabled && !notificationShown.getAndSet(true)) warnNotLoggable()
  logger.trace(message)
}

internal fun logTrace(throwable: Throwable) = catchAndSuppress {
  if (!Registry.`is`("org.jetbrains.idea.reposearch.log.debug", false)) return
  if (!logger.isDebugEnabled && !notificationShown.getAndSet(true)) warnNotLoggable()
  logger.trace(throwable)
}

private val notificationShown = AtomicBoolean(false)

private fun warnNotLoggable() {
  logger.info(
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

private fun isLoggable(ex: Throwable?) = when (ex) {
  is CancellationException, is ProcessCanceledException -> false
  else -> true
}

private fun isNotLoggable(ex: Throwable?) = !isLoggable(ex)

private val traceId = AtomicInteger(0)

data class TraceInfo(
  val source: TraceSource,
  val id: Int = traceId.incrementAndGet()
) {

  override fun toString() = "[$id, source=${source.name}]"

  enum class TraceSource {
    HTTP_CLIENT,
    NONE
  }

  companion object {

    val EMPTY = TraceInfo(TraceSource.NONE, -1)
  }
}
