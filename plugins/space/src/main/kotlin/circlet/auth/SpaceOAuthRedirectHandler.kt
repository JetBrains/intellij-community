package circlet.auth

import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.asCoroutineDispatcher
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

internal suspend fun startRedirectHandling(lifetime: Lifetime, ports: Collection<Int>): SpaceRedirectHandlingInfo? {
  val redirectUrl = CompletableDeferred<String>()
  val freePort = occupyAvailablePort(lifetime, ports) { serverSocket ->
    val socket: Socket = serverSocket.accept()
    socket.getInputStream().use { inputStream ->
      BufferedReader(InputStreamReader(inputStream)).use { reader ->
        var line = reader.readLine()

        line = line.substringAfter("/").substringBefore(" ")

        redirectUrl.complete(line)

        PrintWriter(socket.getOutputStream()).use { out ->
          val response = "<script>close()</script>"
          out.println("HTTP/1.1 200 OK")
          out.println("Content-Type: text/html")
          out.println("Content-Length: " + response.length)
          out.println()
          out.println(response)
          out.flush()
        }
      }
    }
  } ?: return null
  return SpaceRedirectHandlingInfo(freePort, redirectUrl)
}

private suspend fun occupyAvailablePort(lifetime: Lifetime, ports: Collection<Int>, withFreePort: (ServerSocket) -> Unit): Int? {
  val reservedPort = CompletableDeferred<Int?>()
  launch(lifetime, AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()) {
    for (port in ports.distinct().shuffled()) {
      try {
        ServerSocket(port).use { socket ->
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