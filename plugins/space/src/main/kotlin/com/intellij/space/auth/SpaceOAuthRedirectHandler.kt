// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.auth

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.request.uri
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import java.io.IOException

private val ioDispatcher = AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()

internal suspend fun startRedirectHandling(lifetime: Lifetime, server: String, ports: Collection<Int>): SpaceRedirectHandlingInfo? {
  val info = CompletableDeferred<SpaceRedirectHandlingInfo?>()
  launch(lifetime, ioDispatcher) {
    for (port in ports.distinct().shuffled()) {
      try {
        val redirectUrl = startAuthServerAsync(lifetime, server, port)
        info.complete(SpaceRedirectHandlingInfo(port, redirectUrl))
        break
      }
      catch (e: IOException) {
        // check next port
      }
    }
    info.complete(null)
  }
  return info.await()
}

private fun startAuthServerAsync(lifetime: Lifetime, serverUrl: String, port: Int): Deferred<String> {
  val redirectUrl = CompletableDeferred<String>()
  val server = embeddedServer(Netty, port = port, host = "localhost") {
    routing {
      get("/auth") {
        call.respondText(
          createAuthPage(serverUrl),
          contentType = ContentType.Text.Html
        )
        redirectUrl.complete(call.request.uri)
      }
    }
  }
  server.start(wait = false)

  fun stopServer() {
    ApplicationManager.getApplication().executeOnPooledThread(Runnable {
      server.stop(1000, 1000)
    })
  }
  lifetime.addOrCallImmediately {
    stopServer()
  }
  redirectUrl.invokeOnCompletion {
    stopServer()
  }

  return redirectUrl
}

internal data class SpaceRedirectHandlingInfo(val port: Int, val redirectUrl: Deferred<String>)