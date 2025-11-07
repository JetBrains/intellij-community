package com.intellij.mcpserver.impl.util.network

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceOrNull
import java.net.InetAddress

@Service(Service.Level.APP)
class McpServerConnectionAddressProvider {

  private val loopbackHost: String = InetAddress.getLoopbackAddress().hostAddress

  private val serverService: McpServerService
    get() = service()

  val currentHost: String
    get() = effectiveHost(null)

  val currentPort: Int
    get() = serverService.port

  val serverStreamUrl: String
    get() = httpUrl("/stream")

  val serverSseUrl: String
    get() = httpUrl("/sse")

  fun httpUrl(path: String, portOverride: Int? = null, hostOverride: String? = null): String {
    val base = buildBaseUrl(portOverride, hostOverride)
    if (path.isEmpty()) return base
    val normalizedPath = if (path.startsWith("/")) path else "/$path"
    return base + normalizedPath
  }

  private fun buildBaseUrl(portOverride: Int? = null, hostOverride: String? = null): String {
    val host = effectiveHost(hostOverride)
    val port = portOverride ?: currentPort
    return "http://${formatHostForUrl(host)}:$port"
  }

  private fun effectiveHost(hostOverride: String?): String {
    val candidate = hostOverride ?: serverService.resolvedConnectorHost().takeUnless { it.isNullOrBlank() }
    return candidate?.normalizeConnectorHost() ?: loopbackHost
  }

  private fun String.normalizeConnectorHost(): String {
    val trimmed = trim()
    return when (trimmed) {
      "", "*", "0.0.0.0" -> loopbackHost
      "::", "::0", "0:0:0:0:0:0:0:0" -> loopbackHost
      else -> trimmed
    }
  }

  private fun formatHostForUrl(host: String): String =
    if (host.contains(':') && !(host.startsWith("[") && host.endsWith("]"))) "[$host]" else host

  companion object {

    fun getInstanceOrNull(): McpServerConnectionAddressProvider? {
      return serviceOrNull<McpServerConnectionAddressProvider>()
    }
  }
}