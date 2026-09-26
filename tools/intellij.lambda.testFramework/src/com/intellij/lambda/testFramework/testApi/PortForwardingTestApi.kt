package com.intellij.lambda.testFramework.testApi

import com.intellij.lambda.testFramework.frameworkLogger
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

fun prepareServerWithLightContent(portToOccupy: Int = 0): Pair<Int, HttpServer> {
  val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", portToOccupy), 0)
  frameworkLogger.info("HTTP server bound to ${httpServer.address}")
  val serverPort = httpServer.address.port

  val response = "<a href=\"https://www.youtube.com/watch?v=dQw4w9WgXcQ\">click me!</a>"
  httpServer.createContext("/", HttpHandler {
    it.sendResponseHeaders(200, response.length.toLong())
    it.responseBody.use { rb -> rb.write(response.encodeToByteArray()) }
  })

  frameworkLogger.info("Starting http server at ${httpServer.address}")
  httpServer.start()

  return Pair(serverPort, httpServer)
}
