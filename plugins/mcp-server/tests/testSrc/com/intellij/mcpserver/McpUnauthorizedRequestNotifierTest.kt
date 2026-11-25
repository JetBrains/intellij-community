// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.util.network.McpServerConnectionAddressProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.serviceOrNull
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class McpUnauthorizedRequestNotifierTest {
  @Test
  fun `frontend registers unauthorized request notifier`() {
    val notifier = serviceOrNull<McpUnauthorizedRequestNotifier>()
    assertThat(notifier).isNotNull()
    assertThat(notifier!!.javaClass.name)
      .isEqualTo("com.intellij.mcpserver.frontend.util.McpUnauthorizedRequestNotifierImpl")
  }

  @Test
  fun `unauthorized request notifies registered service`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val notifiedPort = CompletableDeferred<Int>()
    application.replaceService(
      McpUnauthorizedRequestNotifier::class.java,
      object : McpUnauthorizedRequestNotifier {
        override suspend fun notifyUnauthorizedRequest(port: Int) {
          notifiedPort.complete(port)
        }
      },
      disposable,
    )

    val serverService = McpServerService.getInstance()
    serverService.start()
    try {
      val sseUrl = requireNotNull(McpServerConnectionAddressProvider.getInstanceOrNull()).serverSseUrl
      val status = HttpClient().use { client ->
        client.get(sseUrl).status
      }
      assertThat(status).isEqualTo(HttpStatusCode.Unauthorized)
      assertThat(notifiedPort.await()).isEqualTo(serverService.port)
    }
    finally {
      serverService.stop()
    }
  }
}
