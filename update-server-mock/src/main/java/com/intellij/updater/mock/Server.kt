/*
 * Copyright (c) 2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.updater.mock

import com.sun.net.httpserver.HttpServer
import org.apache.logging.log4j.LogManager
import java.net.HttpURLConnection.*
import java.net.InetSocketAddress
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class Server(private val port: Int, private val generator: Generator) {
  private val server = HttpServer.create()
  private val log = LogManager.getLogger(Server::class.java)
  private val buildFormat = "([A-Z]+)-([0-9.]+)".toRegex()
  private val tsFormat = DateTimeFormatter.ofPattern("dd/MMM/yyyy:kk:mm:ss ZZ")

  fun start() {
    server.bind(InetSocketAddress("localhost", port), 0)

    server.createContext("/") { ex ->
      val response = try {
        process(ex.requestMethod, ex.requestURI)
      }
      catch(e: Exception) {
        log.error(e)
        Response(HTTP_INTERNAL_ERROR, "Internal error")
      }

      try {
        val contentType = if (response.type.startsWith("text/")) "${response.type}; charset=utf-8" else response.type
        ex.responseHeaders.add("Content-Type", contentType)
        ex.sendResponseHeaders(response.code, response.bytes.size.toLong())
        ex.responseBody.write(response.bytes)
        ex.close()

        log.info("${ex.remoteAddress.address.hostAddress} - - [${tsFormat.format(ZonedDateTime.now())}]" +
          " \"${ex.requestMethod} ${ex.requestURI}\" ${ex.responseCode} ${response.bytes.size}")
      }
      catch(e: Exception) {
        log.error(e)
      }
    }

    server.start()
  }

  private fun process(method: String, uri: URI): Response {
    val path = uri.path
    return when {
      method != "GET" -> Response(HTTP_BAD_REQUEST, "Didn't get")
      path == "/" -> Response(HTTP_OK, "Mock Update Server")
      path == "/updates/updates.xml" -> xml(uri.query ?: "")
      path.startsWith("/patches/") -> patch(path)
      else -> Response(HTTP_NOT_FOUND, "Miss")
    }
  }

  private fun xml(query: String): Response {
    val parameters = query.splitToSequence('&')
      .filter { it.startsWith("build") || it.startsWith("eap") }
      .map { it.split('=', limit = 2) }
      .map { it[0] to if (it.size > 1) it[1] else "" }
      .toMap()

    val build = parameters["build"]
    if (build != null) {
      val match = buildFormat.find(build)
      val productCode = match?.groups?.get(1)?.value
      val buildId = match?.groups?.get(2)?.value
      if (productCode != null && buildId != null) {
        val xml = generator.generateXml(productCode, buildId, "eap" in parameters)
        return Response(HTTP_OK, "text/xml", xml.toByteArray())
      }
    }

    return Response(HTTP_BAD_REQUEST, "Bad parameters")
  }

  private fun patch(path: String): Response = when {
    path.endsWith(".jar") -> Response(HTTP_OK, "binary/octet-stream", generator.generatePatch())
    else -> Response(HTTP_BAD_REQUEST, "Bad path")
  }

  private class Response(val code: Int, val type: String, val bytes: ByteArray) {
    constructor(code: Int, text: String) : this(code, "text/plain", text.toByteArray())
  }
}