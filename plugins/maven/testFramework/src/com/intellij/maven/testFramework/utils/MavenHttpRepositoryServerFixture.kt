// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.utils

import com.intellij.util.concurrency.AppExecutorUtil
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

private const val LOCALHOST = "127.0.0.1"

class MavenHttpRepositoryServerFixture : AbstractMavenRepositoryServerFixture() {

  override fun url(): String {
    return "http://" + LOCALHOST + ":" + myServer.address.port
  }

  override fun startServer(): HttpServer {
    val server = HttpServer.create()
    server.setExecutor(AppExecutorUtil.getAppExecutorService())
    server.bind(InetSocketAddress(LOCALHOST, 0), 5)
    server.start()
    return server
  }
}