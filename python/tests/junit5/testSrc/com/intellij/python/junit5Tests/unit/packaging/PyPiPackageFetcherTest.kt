// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging

import com.intellij.idea.TestFor
import com.intellij.python.junit5Tests.framework.pypi.mockPyPIServerFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPiPackageFetcher
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@TestApplication
@TestFor(issues = ["PY-90681"])
@Subsystems.PackagingRequirements
@Components.Pip
@Layers.Functional
class PyPiPackageFetcherTest {
  private val mockServerFixture = mockPyPIServerFixture(PyPackage("numpy", "1.0.0"), PyPackage("pandas", "2.0.0"))

  @Test
  fun `download returns the package names of the index`(): Unit = timeoutRunBlocking(1.minutes) {
    val packages = withContext(Dispatchers.IO) {
      PyPiPackageFetcher.fetchPipPackages(mockServerFixture.get().simpleUrl).getOrThrow()
    }

    assertThat(packages).containsExactlyInAnyOrder("numpy", "pandas")
  }

  @Test
  fun `cancellation stops the download before the index ends`(): Unit = timeoutRunBlocking(1.minutes) {
    EndlessIndexServer().use { server ->
      val download = launch(Dispatchers.IO) {
        PyPiPackageFetcher.fetchPipPackages(server.simpleUrl)
      }

      waitUntil { server.linksSent >= 10 }

      download.cancel()
      val stopped = withTimeoutOrNull(20.seconds) { download.join() } != null

      assertThat(stopped).describedAs("the download did not stop within 20 seconds after its cancellation").isTrue()
      assertThat(download.isCancelled).isTrue()
    }
  }
}

private class EndlessIndexServer : AutoCloseable {
  private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
  private val executor = Executors.newCachedThreadPool()
  var linksSent = 0
  val simpleUrl: String
    get() = "http://127.0.0.1:${server.address.port}/simple/"

  init {
    server.executor = executor
    server.createContext("/simple/") { exchange ->
      exchange.responseHeaders.set("Content-Type", "text/html")
      exchange.sendResponseHeaders(200, 0)
      exchange.responseBody.use { body ->
        var index = 0
        while (true) {
          body.write("<a href=\"/simple/pkg-$index/\">pkg-$index</a>\n".toByteArray())
          body.flush()
          linksSent++
          index++
        }
      }
    }
    server.start()
  }

  override fun close() {
    server.stop(0)
    executor.shutdownNow()
  }
}
