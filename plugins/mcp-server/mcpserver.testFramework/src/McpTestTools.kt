// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.testFramework

import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolsProvider
import com.intellij.mcpserver.impl.util.asTool
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.common.waitUntil
import com.intellij.util.application
import kotlinx.coroutines.delay
import kotlin.reflect.KFunction
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

suspend fun <T> withRegisteredTestTools(
  vararg toolFunctions: KFunction<*>,
  updateDelay: Duration = 500.milliseconds,
  action: suspend () -> T,
): T {
  var result: T? = null
  Disposer.newDisposable().use { disposable ->
    application.extensionArea.getExtensionPoint(McpToolsProvider.EP).registerExtension(
      object : McpToolsProvider {
        override fun getTools(): List<McpTool> = toolFunctions.map { toolFunction -> toolFunction.asTool() }
      },
      disposable
    )
    delay(updateDelay)
    result = action()
    delay(updateDelay)
  }
  @Suppress("UNCHECKED_CAST")
  return result as T
}

/** [waitUntil] reports only that a condition held, so the value that satisfied it is returned here. */
suspend fun <T : Any> waitUntilNotNull(
  message: String,
  timeout: Duration = DEFAULT_TEST_TIMEOUT,
  produce: suspend () -> T?,
): T {
  var produced: T? = null
  waitUntil(message, timeout) {
    produced = produce()
    produced != null
  }
  return checkNotNull(produced)
}
