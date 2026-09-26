// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.pypi

import com.jetbrains.python.packaging.PyPackage
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.jetbrains.annotations.TestOnly
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes

@TestOnly
data class MockPyPICredentials(val login: String, val password: String)

/**
 * Minimal PEP 503 (Simple Repository API) mock server backed by JDK [HttpServer].
 *
 * Serves wheels for [packages] from [wheelDir]. If [credentials] are provided,
 * the server requires HTTP Basic authentication and responds with 401 for unauthenticated requests.
 */
@TestOnly
class MockPyPIServer internal constructor(
  wheelDir: Path,
  packages: List<PyPackage>,
  val credentials: MockPyPICredentials?,
) {
  private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
  val port: Int get() = server.address.port
  val simpleUrl: String get() = "http://127.0.0.1:$port/simple/"

  init {
    val expectedCredentials = credentials?.let {
      Base64.getEncoder().encodeToString("${it.login}:${it.password}".toByteArray())
    }

    fun authenticateOrReject(exchange: HttpExchange): Boolean {
      if (expectedCredentials == null) return true
      val authHeader = exchange.requestHeaders.getFirst("Authorization")
      if (authHeader != null && authHeader == "Basic $expectedCredentials") return true
      exchange.responseHeaders.set("WWW-Authenticate", "Basic realm=\"mock-pypi\"")
      exchange.sendResponseHeaders(401, -1)
      exchange.close()
      return false
    }

    // package name -> wheel bytes
    val wheelMap = packages.associate { pkg ->
      val wheelFileName = createMinimalWheel(wheelDir, pkg.name, pkg.version)
      pkg.name to (wheelFileName to wheelDir.resolve(wheelFileName).readBytes())
    }

    server.createContext("/simple/") { exchange ->
      if (!authenticateOrReject(exchange)) return@createContext
      val path = exchange.requestURI.path.trimEnd('/')
      when {
        path == "/simple" -> {
          val links = wheelMap.keys.joinToString("\n") { name ->
            "<a href=\"/simple/$name/\">$name</a>"
          }
          respondHtml(exchange, "<html><body>$links</body></html>")
        }
        else -> {
          val packageName = path.removePrefix("/simple/")
          val entry = wheelMap[packageName]
          if (entry != null) {
            val (wheelFileName, _) = entry
            respondHtml(exchange, "<html><body><a href=\"/packages/$wheelFileName\">$wheelFileName</a></body></html>")
          }
          else {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
          }
        }
      }
    }

    server.createContext("/packages/") { exchange ->
      if (!authenticateOrReject(exchange)) return@createContext
      val fileName = exchange.requestURI.path.removePrefix("/packages/")
      val wheelBytes = wheelMap.values.firstOrNull { it.first == fileName }?.second
      if (wheelBytes != null) {
        exchange.responseHeaders.set("Content-Type", "application/octet-stream")
        exchange.sendResponseHeaders(200, wheelBytes.size.toLong())
        exchange.responseBody.use { it.write(wheelBytes) }
      }
      else {
        exchange.sendResponseHeaders(404, -1)
        exchange.close()
      }
    }

    server.start()
  }

  fun stop() {
    server.stop(0)
  }
}

private fun respondHtml(exchange: HttpExchange, html: String) {
  val bytes = html.toByteArray()
  exchange.responseHeaders.set("Content-Type", "text/html")
  exchange.sendResponseHeaders(200, bytes.size.toLong())
  exchange.responseBody.use { it.write(bytes) }
}

/**
 * Creates a minimal PEP 427 wheel file in [outputDir] and returns the wheel file name.
 */
@TestOnly
internal fun createMinimalWheel(outputDir: Path, packageName: String, version: String): String {
  val normalizedName = packageName.replace("-", "_")
  val wheelFileName = "${normalizedName}-${version}-py3-none-any.whl"
  val distInfoDir = "$normalizedName-$version.dist-info"

  outputDir.createDirectories()
  val wheelPath = outputDir.resolve(wheelFileName)

  ZipOutputStream(wheelPath.outputStream()).use { zos ->
    zos.putNextEntry(ZipEntry("$normalizedName/__init__.py"))
    zos.write("# $packageName $version\n".toByteArray())
    zos.closeEntry()

    zos.putNextEntry(ZipEntry("$distInfoDir/METADATA"))
    zos.write("Metadata-Version: 2.1\nName: $packageName\nVersion: $version\n".toByteArray())
    zos.closeEntry()

    zos.putNextEntry(ZipEntry("$distInfoDir/WHEEL"))
    zos.write("Wheel-Version: 1.0\nGenerator: mock\nRoot-Is-Purelib: true\nTag: py3-none-any\n".toByteArray())
    zos.closeEntry()

    zos.putNextEntry(ZipEntry("$distInfoDir/RECORD"))
    zos.closeEntry()
  }

  return wheelFileName
}
