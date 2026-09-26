// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolCallResult
import com.intellij.mcpserver.McpToolCategory
import com.intellij.mcpserver.McpToolDescriptor
import com.intellij.mcpserver.McpToolSchema
import com.intellij.mcpserver.McpToolsProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

private const val TEST_TOOL_NAME = "tools_list_provider_test_tool"

@TestApplication
internal class McpToolsListProviderTest {
  @Test
  fun `initial async conversion runs providers off the EDT`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val provider = RecordingToolsProvider()
    withContext(Dispatchers.EDT) {
      ExtensionTestUtil.addExtensions(McpToolsProvider.EP, listOf(provider), disposable)
      provider.conversionThreadNames.clear()

      McpToolsListProvider.computeAllMcpToolsAsync()
    }

    assertThat(provider.conversionThreadNames).isNotEmpty()
    assertThat(provider.conversionThreadNames).noneMatch { it.startsWith("AWT-EventQueue") }
  }

  /**
   * IJPL-251556: extension point callbacks are dispatched inside a write action on the EDT when a plugin is loaded, so
   * the reflection-heavy conversion must be deferred to a background coroutine instead of running in the callback.
   */
  @Test
  fun `extension point change is converted off the EDT`(): Unit = timeoutRunBlocking {
    val service = McpServerService.getInstance()
    // warm the tools up, so the assertions below are about the extension point listener rather than the initial load
    service.getAllMcpToolsAsync()
    val toolsStateProvider = service.toolsStateProvider

    val provider = RecordingToolsProvider()
    val disposable = Disposer.newDisposable()
    try {
      withContext(Dispatchers.EDT) {
        ExtensionTestUtil.addExtensions(McpToolsProvider.EP, listOf(provider), disposable)
      }

      toolsStateProvider.allTools.first { tools -> tools.any { it.descriptor.name == TEST_TOOL_NAME } }

      assertThat(provider.conversionThreadNames).isNotEmpty()
      assertThat(provider.conversionThreadNames).noneMatch { it.startsWith("AWT-EventQueue") }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  /**
   * An unloaded plugin's tools must be gone by the time the write action finishes, without waiting for the
   * recomputation, so that the plugin's toolset instances are not retained.
   */
  @Test
  fun `extension removal drops the tools of the removed provider immediately`(): Unit = timeoutRunBlocking {
    val service = McpServerService.getInstance()
    service.getAllMcpToolsAsync()
    val toolsStateProvider = service.toolsStateProvider

    val disposable = Disposer.newDisposable()
    try {
      ExtensionTestUtil.addExtensions(McpToolsProvider.EP, listOf(RecordingToolsProvider()), disposable)
      toolsStateProvider.allTools.first { tools -> tools.any { it.descriptor.name == TEST_TOOL_NAME } }

      Disposer.dispose(disposable)

      assertThat(toolsStateProvider.allTools.value.map { it.descriptor.name }).doesNotContain(TEST_TOOL_NAME)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  /**
   * Unloading the MCP server plugin cancels the scope the extension point listeners are bound to, so
   * `extensionRemoved` is never delivered for its own toolsets. The converted tools must be released anyway, otherwise
   * they keep the toolset instances - and through them the contributing plugins' classloaders - alive.
   */
  @Test
  fun `completing the owning scope releases the converted tools`(): Unit = timeoutRunBlocking {
    @Suppress("RAW_SCOPE_CREATION")
    val scope = CoroutineScope(SupervisorJob())
    val provider = McpToolsListProvider(scope, McpToolsListProvider.computeAllMcpToolsAsync())

    assertThat(provider.allTools.value).isNotEmpty()

    scope.cancel()
    scope.coroutineContext.job.join()

    assertThat(provider.allTools.value).isEmpty()
  }

  private class RecordingToolsProvider : McpToolsProvider {
    val conversionThreadNames: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun getTools(): List<McpTool> {
      conversionThreadNames.add(Thread.currentThread().name)
      return listOf(TestTool)
    }
  }

  private object TestTool : McpTool {
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
      name = TEST_TOOL_NAME,
      description = TEST_TOOL_NAME,
      category = McpToolCategory(shortName = "Test", fullyQualifiedName = "test.category"),
      fullyQualifiedName = "test.$TEST_TOOL_NAME",
      inputSchema = McpToolSchema.ofPropertiesSchema(buildJsonObject { }, emptySet(), emptyMap()),
    )

    override suspend fun call(args: JsonObject): McpToolCallResult {
      error("Not needed for tests")
    }
  }
}
