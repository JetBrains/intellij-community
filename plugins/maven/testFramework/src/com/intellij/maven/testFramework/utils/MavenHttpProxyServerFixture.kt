// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.utils

import com.intellij.testFramework.fixtures.IdeaTestFixture
import org.jetbrains.idea.maven.utils.MavenLog
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*
import java.util.concurrent.Executor
import kotlin.jvm.optionals.getOrDefault


class MavenHttpProxyServerFixture(
  val portMap: Map<String, Int>,
  val executor: Executor,
) : IdeaTestFixture {
  private lateinit var myClient: HttpClient
  private lateinit var serverSocket: ServerSocket
  private var running = false

  private var proxyUsername: String? = null
  private var proxyPassword: String? = null
  val requestedFiles: ArrayList<String> = ArrayList<String>()
  val port: Int
    get() = serverSocket.localPort

  override fun setUp() {
    serverSocket = ServerSocket(0);

    myClient = HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .followRedirects(HttpClient.Redirect.NORMAL)
      .connectTimeout(Duration.ofSeconds(10))
      .proxy(HttpClient.Builder.NO_PROXY)
      .build()

    running = true

    executor.execute {
      while (running) {
        acceptConnection()
      }
    }
  }

  private fun acceptConnection() {
    try {
      val socket = serverSocket.accept()
      executor.execute { process(socket) }
    }
    catch (ignore: SocketException) {
    }
    catch (e: IOException) {
      MavenLog.LOG.warn(e)
    }
  }

  private fun process(socket: Socket) {
    try {
      socket.soTimeout = 0
      val os = socket.outputStream
      val iStream = socket.inputStream
      while (!socket.isClosed) {
        val reader = BufferedReader(InputStreamReader(iStream))
        val requestString = reader.readLine()
        if (requestString == null) {
          break
        }
        val index = requestString.indexOf(' ')
        if (index < 0) throw IllegalStateException("Bad Request: $requestString")
        val type: String? = requestString.substring(0, index)
        if (type != "GET") {
          printMethodNotSupportedInfo(requestString, type, os)
          return
        }
        else {
          connectAndLoadData(requestString, reader, os)
        }
      }
    }
    catch (e: IOException) {
      MavenLog.LOG.warn(e)
    }
    finally {
      socket.close()
    }

  }

  private fun connectAndLoadData(requestString: String, reader: BufferedReader, os: OutputStream) {
    val request = requestString.split(' ')
    performGet(request[1], reader, os)
  }


  private fun emptyReader(reader: BufferedReader): List<String> {
    val result = ArrayList<String>()
    while (true) {
      val line = reader.readLine()
      if (line.isEmpty()) break
      result.add(line)
    }
    return result
  }

  private fun performGet(urlString: String, reader: BufferedReader, os: OutputStream) {
    val tempUri = URI.create(if (urlString.startsWith("http", true)) urlString else "http://$urlString")
    val port = portMap[tempUri.host]
    if (port == null) throw IllegalArgumentException("Host ${tempUri.host} is not permitted")
    val clientUri = URI(tempUri.scheme, "$LOCALHOST:$port", tempUri.path, tempUri.query, tempUri.fragment)
    requestedFiles.add(tempUri.path)
    try {
      val headers = emptyReader(reader)
      if (isAuthInfoCorrect(headers)) {
        val serverResponse = makeHttpCall(clientUri)
        writeClientResponse(os, serverResponse)
      }
      else {
        printProxyAuthRequired(os)
      }
      os.flush()
    }
    catch (e: Exception) {
      e.printStackTrace()
    }

  }

  private fun isAuthInfoCorrect(headers: List<String>): Boolean {
    if (proxyUsername == null) return true
    val encodedStr = Base64.getEncoder().encodeToString("$proxyUsername:$proxyPassword".toByteArray())
    val expectedString = "Proxy-Authorization: Basic $encodedStr"
    return headers.contains(expectedString)
  }

  private fun writeClientResponse(os: OutputStream, serverResponse: HttpResponse<ByteArray>) {
    os.writelnString(getResponseString(serverResponse.statusCode()))
    copyHeader(serverResponse, os, "Date")
    copyHeader(serverResponse, os, "Content-type", "text/html")
    os.writelnString("Transfer-encoding: chunked")
    os.writelnString()
    os.writelnString(Integer.toHexString(serverResponse.body().size))
    os.write(serverResponse.body())
    os.writelnString()
    os.writelnString("0")
    os.writelnString()
  }

  private fun makeHttpCall(clientUri: URI): HttpResponse<ByteArray> {
    val request = HttpRequest.newBuilder()
      .uri(clientUri)
      .GET()
      .build()

    return myClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
  }

  private fun copyHeader(response: HttpResponse<ByteArray>, os: OutputStream, name: String, def: String? = null) {
    response.headers().firstValue(name).getOrDefault(def)?.let {
      os.writelnString("$name: $it")
    }
  }

  private fun getResponseString(code: Int): String {
    return when (code) {
      200 -> "HTTP/1.1 200 OK"
      401 -> "HTTP/1.1 401 Unauthorized"
      404 -> "HTTP/1.1 404 Not Found"
      500 -> "HTTP/1.1 500 Internal Server Error"
      else -> TODO()
    }
  }

  override fun tearDown() {
    running = false
    serverSocket.close()
  }

  private fun printMethodNotSupportedInfo(requestString: String, type: String?, os: OutputStream) {
    os.writelnString("HTTP/1.1 405 Method Not Allowed")
    os.writelnString("Content-Type: text/html")
    os.writelnString()
    os.writelnString("""
      <html>
        <head>
          <title>405 Method $type is not supported by ${this.javaClass.simpleName}</title>
        </head>
        <body>
          <h1>405 Not supported</h1>
          <p>Bad HTTP request line: $requestString</p>
        </body>
      </html>""")
  }

  private fun printProxyAuthRequired(os: OutputStream) {
    os.writelnString("HTTP/1.1 407 Proxy Authentication Required")
    os.writelnString("Content-Type: text/html")
    os.writelnString("Proxy-Authenticate: Basic realm=\"MavenHttpProxyServerFixture\"")
    os.writelnString()
    os.writelnString("""
      <html>
        <head>
          <title>407 Proxy Authentication Required</title>
        </head>
        <body>
          <h1>407 Proxy Authentication Required</h1>
          <p>use $proxyUsername:$proxyPassword as credentials</p>
        </body>
      </html>""")
  }

  fun requireAuthentication(username: String, password: String) {
    proxyUsername = username
    proxyPassword = password
  }

  companion object {
    private const val LOCALHOST = "127.0.0.1"
  }
}

private fun OutputStream.writeString(string: String) {
  this.write(string.toByteArray())
}

private fun OutputStream.writelnString(string: String = "") {
  writeString("$string\r\n")
}
