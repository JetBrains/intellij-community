// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.execution.ExecutionException
import com.intellij.execution.target.HostPort
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.EelConnectionError
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.getAcceptorForRemotePort
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.asEelChannel
import com.intellij.platform.eel.provider.utils.asInetAddress
import com.intellij.platform.eel.provider.utils.consumeAsEelChannel
import com.intellij.platform.eel.provider.utils.copy
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

private val LOG = logger<PyEelDebuggerTunnel>()

/**
 * A port on a remote eel that forwards each connection to an IDE server socket, for example of pydevd or of the Python console.
 * An SDK without a target can sit on a remote eel, for example WSL. There, `127.0.0.1` is not the machine of the IDE.
 */
@ApiStatus.Internal
class PyEelDebuggerTunnel private constructor(
  /** The address that the process on the eel connects to. */
  val hostPort: HostPort,
  private val job: Job,
) {
  fun close() {
    job.cancel()
  }

  companion object {
    /**
     * Opens a port on [eel] that forwards to [ideServer], a server socket of this IDE process.
     * The port listens on `localhost` of [eel], and [hostPort] is the address that it is bound to.
     * The IDE side is a plain socket, because the server runs in this JVM.
     */
    @JvmStatic
    @Throws(ExecutionException::class)
    @RequiresBackgroundThread
    fun open(eel: EelDescriptor, ideServer: InetSocketAddress): PyEelDebuggerTunnel = runBlockingMaybeCancellable {
      val acceptor = try {
        eel.toEelApi().tunnels.getAcceptorForRemotePort().eelIt()
      }
      catch (e: EelConnectionError) {
        throw ExecutionException(e)
      }
      val boundAddress = acceptor.boundAddress.asInetAddress()
      val job = PythonDebuggerScope.childScope(name = "pydevd tunnel from $eel").launch(Dispatchers.IO) {
        try {
          for (connection in acceptor.incomingConnections) {
            launch { forward(connection, ideServer) }
          }
        }
        finally {
          acceptor.close()
          LOG.info("The tunnel from $boundAddress on $eel to $ideServer is closed")
        }
      }
      PyEelDebuggerTunnel(HostPort(boundAddress.address.hostAddress, boundAddress.port), job)
    }

    /**
     * Copies the data of [connection] to and from a new socket to [ideServer] until both sides end.
     *
     * Nobody waits for the result. A failure closes both sockets of the connection, so the process on the eel and the IDE
     * server see a lost connection, like a crash of a local process. The other connections, for example of subprocesses, go on.
     */
    private suspend fun forward(connection: EelTunnelsApi.Connection, ideServer: InetSocketAddress) {
      try {
        Socket().use { socket ->
          socket.connect(ideServer)
          coroutineScope {
            launch {
              copy(connection.receiveChannel, socket.asEelChannel())
              // The process on the eel closed its side, so the IDE server reads the end of the stream
              socket.shutdownOutput()
            }
            launch {
              copy(socket.consumeAsEelChannel(), connection.sendChannel)
            }
          }
        }
      }
      catch (e: IOException) {
        LOG.info("A connection through the tunnel to $ideServer failed", e)
      }
      finally {
        connection.close()
      }
    }
  }
}
