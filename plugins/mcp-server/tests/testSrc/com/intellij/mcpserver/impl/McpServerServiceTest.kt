package com.intellij.mcpserver.impl

import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import com.intellij.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@TestApplication
class McpServerServiceTest {

  private lateinit var disposable: com.intellij.openapi.Disposable
  private lateinit var settings: McpServerSettings
  private lateinit var scope: CoroutineScope
  private var reservedSocket: ServerSocket? = null

  @BeforeEach
  fun setup() {
    disposable = Disposer.newDisposable()
    settings = McpServerSettings()
    ApplicationManager.getApplication().replaceService(McpServerSettings::class.java, settings, disposable)
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    Disposer.register(disposable) { scope.cancel() }
  }

  @AfterEach
  fun teardown() {
    reservedSocket?.close()
    Disposer.dispose(disposable)
  }

  data class PortCase(
    val name: String,
    val conflict: Boolean,
    val enableInSettingsFirst: Boolean,
    val testReuse: Boolean = false
  )

  companion object {
    @JvmStatic
    fun portCases(): List<Arguments> = listOf(
      Arguments.of(PortCase("startup_no_conflict", conflict = false, enableInSettingsFirst = true)),
      Arguments.of(PortCase("startup_conflict", conflict = true, enableInSettingsFirst = true)),
      Arguments.of(PortCase("ui_enable_no_conflict", conflict = false, enableInSettingsFirst = false)),
      Arguments.of(PortCase("ui_enable_conflict", conflict = true, enableInSettingsFirst = false)),
      Arguments.of(PortCase("reuse_no_conflict", conflict = false, enableInSettingsFirst = false, testReuse = true))
    )

    private fun reservePort(): ServerSocket {
      val port = NetworkUtils.findFreePort()
      check(port != -1) { "No free port available" } // Add check for exhaustion
      return ServerSocket(port, 0, InetAddress.getByName("127.0.0.1"))
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("portCases")
  fun mcpServerPortPersists(case: PortCase) = runTest(timeout = 10.seconds) {
    reservedSocket = if (case.conflict) reservePort() else null
    val desiredPort = reservedSocket?.localPort ?: NetworkUtils.findFreePort()
    settings.state.mcpServerPort = desiredPort
    settings.state.enableMcpServer = case.enableInSettingsFirst

    val context = TestContext(settings, desiredPort, reservedSocket, disposable, scope)
    context.installService()

    try {
      startServer(context, case)
      waitForServerStart(context)
      assertPortBehavior(context, case, desiredPort)
    } finally {
      context.stopServiceIfStarted()
      advanceUntilIdle() // Ensure cleanup
    }
  }

  private suspend fun startServer(context: TestContext, case: PortCase) {
    if (!case.enableInSettingsFirst) {
      context.runningService().start()
    }
  }

  private suspend fun waitForServerStart(context: TestContext) {
    val timeout = 5.seconds.inWholeMilliseconds
    var elapsed = 0L
    while (!context.runningService().isRunning && elapsed < timeout) {
      delay(100)
      elapsed += 100
    }
    check(context.runningService().isRunning) { "Server failed to start within timeout" }
  }

  private fun assertPortBehavior(context: TestContext, case: PortCase, desiredPort: Int) {
    if (case.testReuse) {
      val initialPort = context.runningService().port
      val initialPersisted = settings.state.mcpServerPort
      context.runningService().start() // Reuse attempt
      assertEquals(initialPort, context.runningService().port, "Port should be reused")
      assertEquals(initialPersisted, settings.state.mcpServerPort, "Persisted port should remain unchanged")
    } else {
      if (case.conflict) {
        waitForCondition("Expected port to change on conflict") { settings.state.mcpServerPort != desiredPort }
      } else {
        waitForCondition("Expected port to persist without conflict") { settings.state.mcpServerPort == context.runningService().port }
        assertEquals(desiredPort, settings.state.mcpServerPort, "Port should match desired without conflict")
      }
      assertEquals(context.runningService().port, settings.state.mcpServerPort, "Running port should match persisted port")
    }
  }

  private fun waitForCondition(message: String, condition: () -> Boolean) {
    val timeout = 5000L // ms
    var elapsed = 0L
    while (!condition() && elapsed < timeout) {
      Thread.sleep(100)
      elapsed += 100
    }
    check(condition()) { "$message (timed out)" }
  }

  internal class TestContext(
    val settings: McpServerSettings,
    val desiredPort: Int,
    val reservedSocket: ServerSocket?,
    private val disposable: com.intellij.openapi.Disposable,
    private val scope: CoroutineScope,
  ) {
    private lateinit var service: McpServerService

    fun installService() {
      service = McpServerService(scope)
      ApplicationManager.getApplication().replaceService(McpServerService::class.java, service, disposable)
    }

    fun stopServiceIfStarted() {
      if (this::service.isInitialized) {
        service.stop()
      }
    }

    fun runningService(): McpServerService = service
  }
}
