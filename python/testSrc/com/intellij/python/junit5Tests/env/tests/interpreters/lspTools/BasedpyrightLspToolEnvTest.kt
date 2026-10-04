// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.interpreters.lspTools

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.platform.lsp.api.Lsp4jServer
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.getClients
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pyright.BasedpyrightConfiguration
import com.intellij.python.pyright.BasedpyrightPyTool
import com.intellij.python.pyright.PyrightLspClientDescriptor
import com.intellij.python.pyright.PyrightLspIntegrationProvider
import com.intellij.python.test.env.junit5.pyVenvFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.eclipse.lsp4j.services.WorkspaceService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

/**
 * End-to-end test of the Pyright LSP tool support against the real `basedpyright` language server.
 *
 * [BasedpyrightPyTool] installs the `basedpyright` package and the binary is resolved as
 * `basedpyright-langserver`. The test verifies that a type error reported via
 * `textDocument/publishDiagnostics` is surfaced as an editor error highlight.
 */
@Subsystems.LspTools
@Layers.Functional
@TestApplication
@PyEnvTestCase
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class BasedpyrightLspToolEnvTest {
  private suspend fun enablePyrightAndInstall() = pyProject.enableLspToolAndInstall(
    project = project,
    pyTool = BasedpyrightPyTool.getInstance(),
    toolInstalled = toolInstalled,
  ) {
    project.service<BasedpyrightConfiguration>().apply {
      inspections = true
    }
  }

  @Test
  fun `reports a type error diagnostic`(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    enablePyrightAndInstall()
    val file = codeInsightFixture.configureByText("typecheck.py", """
      x: int = "not an int"
    """.trimIndent())
    val errors = awaitLspErrorDiagnostics(project, file.virtualFile, PyrightLspIntegrationProvider::class.java)
    assertReportsIntAssignmentError(errors, "basedpyright")
  }

  @Test
  @TestFor(issues = ["PY-92860"], classes = [PyrightLspClientDescriptor::class])
  fun `sends one didChangeConfiguration to a started server after several descriptor builds`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    val configurationChanges = ConcurrentHashMap<LspClient, AtomicInteger>()
    LspClientManager.getInstance(project).addLsp4jServerWrapper({ client, server ->
      if (client.providerClass == PyrightLspIntegrationProvider::class.java) {
        countConfigurationChanges(server, configurationChanges.computeIfAbsent(client) { AtomicInteger() })
      }
      else {
        server
      }
    }, disposable)
    repeat(3) { PyrightLspClientDescriptor(module) }

    enablePyrightAndInstall()
    val file = codeInsightFixture.configureByText("typecheck.py", """
      x: int = "not an int"
    """.trimIndent())
    awaitLspErrorDiagnostics(project, file.virtualFile, PyrightLspIntegrationProvider::class.java)

    val client = LspClientManager.getInstance(project).getClients<PyrightLspIntegrationProvider>()
      .single { it.state == LspServerState.Running }
    assertEquals(1, configurationChanges[client]?.get())
  }

  @AfterEach
  fun tearDownTool(): Unit = timeoutRunBlocking {
    tearDownLspTool(project, PyrightLspIntegrationProvider::class.java)
  }

  /**
   * Wraps [server], so that each `workspace/didChangeConfiguration` the IDE sends to it increments [count].
   * The wrappers are proxies, because Kotlin delegation does not forward the Java default methods, such as `initialized`.
   */
  private fun countConfigurationChanges(server: Lsp4jServer, count: AtomicInteger): Lsp4jServer =
    forwardingProxy(server, Lsp4jServer::class.java) { method, args ->
      if (method.name == "getWorkspaceService") {
        val workspaceService = server.workspaceService
        forwardingProxy(workspaceService, WorkspaceService::class.java) { workspaceMethod, workspaceArgs ->
          if (workspaceMethod.name == "didChangeConfiguration") count.incrementAndGet()
          workspaceMethod.invokeUnwrapped(workspaceService, workspaceArgs)
        }
      }
      else {
        method.invokeUnwrapped(server, args)
      }
    }

  private fun <T : Any> forwardingProxy(delegate: T, type: Class<T>, handler: (Method, Array<out Any?>) -> Any?): T {
    val interfaces = (delegate.javaClass.interfaces.toSet() + type).toTypedArray()
    return type.cast(Proxy.newProxyInstance(delegate.javaClass.classLoader, interfaces) { _, method, args ->
      handler(method, args.orEmpty())
    })
  }

  private fun Method.invokeUnwrapped(target: Any, args: Array<out Any?>): Any? =
    try {
      invoke(target, *args)
    }
    catch (e: InvocationTargetException) {
      throw e.targetException
    }

  companion object {
    private val toolInstalled = AtomicBoolean(false)
    private val tempPathFixture = tempPathFixture()
    private val projectFixture = projectFixture(openAfterCreation = true)
    internal val project by projectFixture
    private val moduleFixture = projectFixture.pyModuleFixture(tempPathFixture, addPathToSourceRoot = true)
    internal val module by moduleFixture
    internal val pyProject by moduleFixture.pyProjectFixture()
    internal val venv by pySdkFixture().pyVenvFixture(
      where = tempPathFixture,
      addToSdkTable = true,
      moduleFixture = moduleFixture,
    )
    internal val codeInsightFixture by codeInsightFixture(projectFixture, tempPathFixture)
  }
}
