// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5

import com.intellij.python.test.env.core.PyEnvDownloadCache
import com.intellij.python.test.env.core.PyEnvHttpConnection
import com.intellij.python.test.env.core.PyEnvHttpConnectionFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

@Subsystems.Interpreters
@Layers.Functional
internal class PyEnvDownloadCacheTest {
  @TempDir
  lateinit var cacheDirectory: Path

  @Test
  fun `cached download does not repeat the request`(): Unit = timeoutRunBlocking {
    val requestCount = AtomicInteger()
    val cache = PyEnvDownloadCache(cacheDirectory, PyEnvHttpConnectionFactory {
      requestCount.incrementAndGet()
      TestHttpConnection("metadata")
    })

    val cachedFile = cache.getOrDownload("https://example.test/metadata", "metadata")
    val second = cache.getOrDownload("https://example.test/metadata", "metadata")

    assertThat(second).isEqualTo(cachedFile)
    assertThat(requestCount.get()).isEqualTo(1)
  }

  @Test
  fun `concurrent callers share checksum recovery`(): Unit = timeoutRunBlocking {
    val requestCount = AtomicInteger()
    val cache = PyEnvDownloadCache(cacheDirectory, PyEnvHttpConnectionFactory {
      val content = if (requestCount.getAndIncrement() == 0) "invalid" else "valid"
      TestHttpConnection(content)
    })

    val files = coroutineScope {
      List(2) {
        async {
          cache.getOrDownload("https://example.test/asset", "tool") { Files.readString(it) == "valid" }
        }
      }.awaitAll()
    }

    assertThat(files.map(Files::readString)).containsExactly("valid", "valid")
    assertThat(requestCount.get()).isEqualTo(2)
  }

  private class TestHttpConnection(content: String) : PyEnvHttpConnection {
    override val responseCode: Int = 200
    override val inputStream: InputStream = ByteArrayInputStream(content.toByteArray())

    override fun close() {
    }
  }
}
