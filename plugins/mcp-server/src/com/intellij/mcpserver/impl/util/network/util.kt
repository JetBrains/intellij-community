package com.intellij.mcpserver.impl.util.network

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.host
import io.ktor.server.response.respond
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL

private val logger = fileLogger()

internal fun isPortAvailable(port: Int): Boolean {
  return try {
    ServerSocket(port, 0, InetAddress.getLoopbackAddress()).close()
    true
  }
  catch (_: Exception) {
    false
  }
}

internal fun findFirstFreePort(startingPort: Int): Int {
  var port = startingPort
  while (!isPortAvailable(port)) {
    port++
    if (port > 65535) {
      throw IllegalStateException("No free ports available")
    }
  }
  return port
}

// Define the set of allowed hostnames
private val allowedHosts = setOf("127.0.0.1", "localhost", "::1", "[::1]")

/**
 * Installs host validation middleware to protect against DNS rebinding attacks.
 *
 * DNS rebinding is an attack where:
 * 1. Attacker tricks victim into visiting attacker.com
 * 2. attacker.com initially resolves to attacker's server
 * 3. JavaScript from attacker's server runs in victim's browser
 * 4. Attacker changes DNS to point attacker.com to 127.0.0.1
 * 5. JavaScript can now make requests to local services bypassing same-origin policy
 *
 * Protection strategy:
 * - Validate Host header against a whitelist of allowed local hostnames
 * - Check both Host header and Origin header
 * - Reject requests with unexpected Host values
 */
fun Application.installHostValidation() {
  intercept(ApplicationCallPipeline.Setup) {
    val hostHeader = call.request.host()
    val origin = call.request.headers["Origin"]
    val referer = call.request.headers["Referer"]

    // Check Host header
    if (!isAllowedHost(hostHeader)) {
      call.respond(HttpStatusCode.Forbidden)
      finish()
      return@intercept
    }

    // Additional check: if Origin or Referer is present, validate them too
    if (origin != null && !isAllowedOrigin(origin)) {
      logger.trace { "Origin $origin is not a local origin. Disallowed." }
      call.respond(HttpStatusCode.Forbidden)
      finish()
      return@intercept
    }

    if (referer != null && !isAllowedOrigin(referer)) {
      logger.trace { "Referer $referer is not a local origin. Disallowed." }
      call.respond(HttpStatusCode.Forbidden)
      finish()
      return@intercept
    }
  }
}

private fun isAllowedHost(host: String): Boolean {
  // Remove port if present
  val hostname = host.substringBefore(':').trim()

  // Quick check against allowed hosts
  if (hostname in allowedHosts) {
    logger.trace { "Host $host is allowed by list of allowed hosts" }
    return true
  }
  return false
}

private fun isAllowedOrigin(uri: String): Boolean {
  return try {
    logger.trace { "Checking origin $uri against allowed hosts..." }
    val url = URL(uri)
    isAllowedHost(url.host)
  }
  catch (_: Exception) {
    logger.trace { "Origin $uri is not a valid URL. Disallowed." }
    false
  }
}