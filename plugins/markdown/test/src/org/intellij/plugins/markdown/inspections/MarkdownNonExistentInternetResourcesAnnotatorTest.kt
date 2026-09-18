// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.inspections

import com.intellij.markdown.backend.inspections.MarkdownNonExistentInternetResourceInspection
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class MarkdownNonExistentInternetResourcesAnnotatorTest : BasePlatformTestCase() {
  fun testWebLinks() {
    TestHttpServer().use { server ->
      myFixture.enableInspections(MarkdownNonExistentInternetResourceInspection())
      val root = server.rootUrl
      myFixture.configureByText("links.md", """
        [Available]($root/available)
        [Missing](<warning>$root/missing</warning>)
        <<warning>$root/missing-autolink</warning>>
      """.trimIndent())

      myFixture.checkHighlighting()
      assertEquals(3, server.requestCount.get())
    }
  }

  private class TestHttpServer : AutoCloseable {
    val requestCount = AtomicInteger()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
      createContext("/") { exchange ->
        requestCount.incrementAndGet()
        val status = if (exchange.requestURI.path.startsWith("/missing")) 404 else 200
        exchange.sendResponseHeaders(status, -1)
        exchange.close()
      }
      start()
    }

    val rootUrl: String = "http://127.0.0.1:${server.address.port}"

    override fun close() {
      server.stop(0)
    }
  }
}
