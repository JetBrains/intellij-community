package com.intellij.space.auth

import com.intellij.util.concurrency.AppExecutorUtil
import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.*
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import java.io.IOException

private val ioDispatcher = AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()

internal suspend fun startRedirectHandling(lifetime: Lifetime, ports: Collection<Int>): SpaceRedirectHandlingInfo? {
  val redirectUrl = CompletableDeferred<String>()
  val freePort = occupyAvailablePort(lifetime, ports) { serverSocket ->
    val socket = serverSocket.accept()
    launch(lifetime, ioDispatcher) {
      val response = "<script>close()</script>"
      val output = socket.openWriteChannel(autoFlush = true)
      output.writeStringUtf8(
        """
        HTTP/1.1 200 OK
        Content-Type: text/html
        Content-Length: ${response.length}
        
        $response
      """.trimIndent()
      )
    }

    val input = socket.openReadChannel()
    val line = input.readUTF8Line()!!.substringAfter("/").substringBefore(" ")
    redirectUrl.complete(line)
  } ?: return null
  return SpaceRedirectHandlingInfo(freePort, redirectUrl)
}

private suspend fun occupyAvailablePort(lifetime: Lifetime, ports: Collection<Int>, withFreePort: suspend (ServerSocket) -> Unit): Int? {
  val reservedPort = CompletableDeferred<Int?>()
  launch(lifetime, ioDispatcher) {
    for (port in ports.distinct().shuffled()) {
      try {
        aSocket(ActorSelectorManager(ioDispatcher)).tcp().bind(port = port).use { socket ->
          reservedPort.complete(port)
          withFreePort(socket)
        }
        break
      }
      catch (e: IOException) {
        if (reservedPort.isCompleted) {
          throw e
        }
        // check next port
      }
    }
    reservedPort.complete(null)
  }
  return reservedPort.await()
}

internal data class SpaceRedirectHandlingInfo(val port: Int, val redirectUrl: Deferred<String>)