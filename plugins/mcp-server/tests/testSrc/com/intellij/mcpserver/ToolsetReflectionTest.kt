package com.intellij.mcpserver

import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.impl.util.asTools
import com.intellij.mcpserver.impl.util.asToolsAsync
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@TestApplication
class ToolsetReflectionTest {
  @Test
  fun `tool conversions run in parallel on Dispatchers Default`() = timeoutRunBlocking {
    val toolset = ParallelToolset()

    val tools = toolset.asToolsAsync()

    assertEquals(setOf("firstTool", "secondTool"), tools.map { it.descriptor.name }.toSet())
    assertTrue(toolset.conversionThreadNames.all { it.startsWith("DefaultDispatcher-worker-") })
  }

  /**
   * IJPL-251556: the synchronous conversion used to wrap the parallel one in `runBlockingMaybeCancellable`,
   * which throws on the EDT.
   */
  @Test
  fun `sequential tool conversion is allowed on the EDT`() = runBlocking(Dispatchers.EDT) {
    val tools = SequentialToolset().asTools()

    assertEquals(setOf("firstTool", "secondTool"), tools.map { it.descriptor.name }.toSet())
  }

  @Test
  fun `tool conversion failure propagates to caller`() {
    val error = assertThrows(IllegalStateException::class.java) {
      FailingToolset().asTools()
    }

    assertEquals("Expected tool conversion failure", error.message)
  }

  @Test
  fun `tool conversion failure propagates to caller of the parallel conversion`() {
    val error = assertThrows(IllegalStateException::class.java) {
      runBlocking { FailingToolset().asToolsAsync() }
    }

    assertEquals("Expected tool conversion failure", error.message)
  }

  @Test
  fun `toolset user configurability is propagated to reflected tools`() {
    val tools = LaunchManagedToolset().asTools()

    assertTrue(tools.none { it.isUserConfigurable })
  }

  private class ParallelToolset : McpToolset {
    val conversionThreadNames: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val firstConversion = AtomicBoolean(true)
    private val secondConversionStarted = CountDownLatch(1)

    override fun displayDescription(toolName: String): String? {
      conversionThreadNames.add(Thread.currentThread().name)
      if (firstConversion.compareAndSet(true, false)) {
        check(secondConversionStarted.await(10, TimeUnit.SECONDS)) { "Tool conversions did not run in parallel" }
      }
      else {
        secondConversionStarted.countDown()
      }
      return null
    }

    @McpTool
    fun firstTool() {
    }

    @McpTool
    fun secondTool() {
    }
  }

  private class SequentialToolset : McpToolset {
    @McpTool
    fun firstTool() {
    }

    @McpTool
    fun secondTool() {
    }
  }

  private class FailingToolset : McpToolset {
    override fun displayDescription(toolName: String): String {
      error("Expected tool conversion failure")
    }

    @McpTool
    fun failingTool() {
    }
  }

  private class LaunchManagedToolset : McpToolset {
    override fun isUserConfigurable(): Boolean = false

    @McpTool
    fun managedTool() {
    }
  }
}
