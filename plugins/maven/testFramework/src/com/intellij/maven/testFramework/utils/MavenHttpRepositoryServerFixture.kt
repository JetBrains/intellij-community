// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.utils

import com.intellij.ReviseWhenPortedToJDK
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.testFramework.fixtures.IdeaTestFixture
import com.intellij.util.concurrency.AppExecutorUtil
import com.sun.net.httpserver.Authenticator
import com.sun.net.httpserver.BasicAuthenticator
import com.sun.net.httpserver.HttpServer
import org.jetbrains.idea.maven.utils.MavenLog
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

@ReviseWhenPortedToJDK("18") //replace with SimpleFileServers
class MavenHttpRepositoryServerFixture : IdeaTestFixture {
  private lateinit var myServer: HttpServer
  fun url(): String {
    if (!this::myServer.isInitialized) {
      throw IllegalStateException("Url is not ready yet, call setUp first")
    }
    return "http://" + LOCALHOST + ":" + myServer.getAddress().port

  }

  override fun setUp() {
    myServer = HttpServer.create()
    myServer.setExecutor(AppExecutorUtil.getAppExecutorService())
    myServer.bind(InetSocketAddress(LOCALHOST, 0), 5)
    myServer.start()
  }

  override fun tearDown() {
    if (this::myServer.isInitialized) {
      myServer.stop(10)
    }
  }

  fun startRepositoryFor(repo: File, contextPath: String = "/", expectedUsername: String? = null, expectedPassword: String? = null) {
    val authenticator: Authenticator? = if (expectedUsername == null) null
    else object : BasicAuthenticator("/") {
      override fun checkCredentials(username: String?, password: String?): Boolean {
        return expectedUsername == username && expectedPassword == password && expectedPassword != null
      }
    }
    setupRemoteRepositoryServer(repo, contextPath, authenticator)
  }

  fun startRepositoryFor(repo: File, expectedUsername: String, expectedPassword: String) {
    startRepositoryFor(repo, "/", expectedUsername, expectedPassword)
  }

  fun startRepositoryFor(repo: File) {
    startRepositoryFor(repo, "/", null, null)
  }

  fun startRepositoryFor(repo: String) {
    startRepositoryFor(File(repo), "/", null, null)
  }

  private fun setupRemoteRepositoryServer(repo: File, contextPath: String, authenticator: Authenticator?) {
    val httpContext = myServer.createContext(contextPath) { exchange ->
      val path = exchange.requestURI.path
      MavenLog.LOG.warn("Got request for $path")
      val file = File(repo, path)
      if (file.isDirectory) {
        exchange.responseHeaders.add("Content-Type", "text/html")
        exchange.sendResponseHeaders(200, 0)
        val list = java.lang.String.join(",\n<br/>", *file.list())
        exchange.responseBody.write(list.toByteArray(StandardCharsets.UTF_8))
      }
      else if (file.isFile) {
        exchange.responseHeaders.add("Content-Type", "application/octet-stream")
        exchange.sendResponseHeaders(200, 0)
        BufferedInputStream(FileInputStream(file)).use {
          StreamUtil.copy(it, exchange.responseBody)
        }
      }
      else {
        exchange.sendResponseHeaders(404, -1)
      }
      exchange.close()
      MavenLog.LOG.warn("Sent response for $path")
    }
    httpContext.authenticator = authenticator
  }

  companion object {
    private const val LOCALHOST = "127.0.0.1"
  }
}