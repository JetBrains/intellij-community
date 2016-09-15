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
package org.jetbrains.updater.mock

import com.sun.net.httpserver.Filter
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.OutputStream
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_OK
import java.net.InetSocketAddress
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class Server(private val port: Int) {
  private val server = HttpServer.create()

  fun start() {
    server.bind(InetSocketAddress("localhost", port), 0)
    server.handle("/updates/updates.xml", HttpHandler { sendUpdatesXml(it) })
    server.handle("/patches/", HttpHandler { sendPatch(it) })
    server.handle("/", HttpHandler { sendText(it, "Mock Update Server") })
    server.start()
  }

  private fun sendText(ex: HttpExchange, data: String, type: String = "text/plain", code: Int = HTTP_OK) {
    val bytes = data.toByteArray()
    ex.responseHeaders.add("Content-Type", "$type; charset=utf-8")
    ex.sendResponseHeaders(code, bytes.size.toLong())
    ex.responseBody.write(bytes)
    ex.close()
  }

  private fun sendUpdatesXml(ex: HttpExchange) {
    var build: String? = null
    var eap = false
    ex.requestURI.query?.splitToSequence('&')?.forEach {
      val p = it.split('=', limit = 2)
      when (p[0]) {
        "build" -> build = if (p.size > 1) p[1] else null
        "eap" -> eap = true
      }
    }
    if (build == null) {
      sendText(ex, "Parameter missing", code = HTTP_BAD_REQUEST)
      return
    }

    val result = "([A-Z]+)-([0-9.]+)".toRegex().find(build!!)
    val productCode = result?.groups?.get(1)?.value
    val buildId = result?.groups?.get(2)?.value
    if (productCode == null || buildId == null) {
      sendText(ex, "Parameter malformed", code = HTTP_BAD_REQUEST)
      return
    }

    val xml = Generator.generateXml(productCode, buildId, eap)
    sendText(ex, xml, "text/xml")
  }

  private fun sendPatch(ex: HttpExchange) {
    if (!ex.requestURI.path.endsWith(".jar")) {
      sendText(ex, "Request malformed", code = HTTP_BAD_REQUEST)
      return
    }

    val patch = Generator.generatePatch()
    ex.responseHeaders.add("Content-Type", "binary/octet-stream")
    ex.sendResponseHeaders(HTTP_OK, patch.size.toLong())
    ex.responseBody.write(patch)
    ex.close()
  }
}

private fun HttpServer.handle(path: String, handler: HttpHandler) {
  val ctx = createContext(path, handler)
  ctx.filters += AccessLogFilter()
}

private class AccessLogFilter : Filter() {
  companion object {
    private val DTF = DateTimeFormatter.ofPattern("dd/MMM/yyyy:kk:mm:ss ZZ")
  }

  override fun description() = "Access Log Filter"

  override fun doFilter(ex: HttpExchange, chain: Chain) {
    val out = CountingOutputStream(ex.responseBody)
    ex.setStreams(ex.requestBody, out)

    try {
      chain.doFilter(ex)
      println("${ex.remoteAddress.address.hostAddress} - - [${DTF.format(ZonedDateTime.now())}]" +
        " \"${ex.requestMethod} ${ex.requestURI}\" ${ex.responseCode} ${out.count}")
    }
    catch(e: Exception) {
      e.printStackTrace()
      ex.close()
    }
  }
}

private class CountingOutputStream(private val stream: OutputStream) : OutputStream() {
  var count: Int = 0
    private set(v) { field = v }

  override fun write(b: Int) {
    stream.write(b)
    count += 1
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    stream.write(b, off, len)
    count += len
  }
}