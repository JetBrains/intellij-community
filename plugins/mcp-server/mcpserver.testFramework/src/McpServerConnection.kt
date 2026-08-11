// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.testFramework

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.util.network.McpServerConnectionAddressProvider
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.openapi.project.Project
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Runs the provided MCP client [action] inside an authorized MCP session bound to [project].
 *
 * This is the single, IDE-agnostic transport helper for MCP tool tests: it opens an authorized session with
 * command execution set to [McpServerService.AskCommandExecutionMode.DONT_ASK], resolves the transport URL via
 * [McpServerConnectionAddressProvider] (falling back to `http://localhost:<port>/stream`), and wires a
 * [StreamableHttpClientTransport] that carries the project-path and auth headers.
 *
 * Pass a customized [client] to override the default plain client (e.g. to declare additional capabilities).
 */
suspend fun <T> withMcpServerConnection(
  project: Project,
  client: Client = Client(Implementation(name = "test client", version = "1.0")),
  requestTimeout: Duration = 5.minutes,
  action: suspend (Client) -> T,
): T {
  var result: Result<T>? = null
  val projectBasePath = project.basePath

  McpServerService.getInstance().authorizedSession(
    McpServerService.McpSessionOptions(
      commandExecutionMode = McpServerService.AskCommandExecutionMode.DONT_ASK,
    ),
  ) { port, authTokenName, authTokenValue ->
    val addressProvider = McpServerConnectionAddressProvider.getInstanceOrNull()
    val transportUrl = addressProvider?.httpUrl("/stream", portOverride = port)
                       ?: "http://localhost:$port/stream"
    val httpClient = HttpClient {
      install(SSE)
      install(HttpTimeout) {
        requestTimeoutMillis = requestTimeout.inWholeMilliseconds
        connectTimeoutMillis = requestTimeout.inWholeMilliseconds
        socketTimeoutMillis = requestTimeout.inWholeMilliseconds
      }
    }
    val transport = StreamableHttpClientTransport(httpClient, transportUrl, requestBuilder = {
      projectBasePath?.let { header(IJ_MCP_SERVER_PROJECT_PATH, it) }
      header(authTokenName, authTokenValue)
    })

    try {
      client.connect(transport)
      result = runCatching {
        action(client)
      }
    }
    finally {
      httpClient.use {
        transport.close()
      }
    }
  }

  return result?.getOrThrow() ?: error("Authorized MCP session finished without producing a client result")
}
