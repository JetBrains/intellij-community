// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin

import com.intellij.execution.target.HostPort
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.getAcceptorForRemotePort
import com.intellij.platform.eel.getShell
import com.intellij.platform.eel.hostAddress
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.utils.sendWholeText
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.EelSource
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.junit5Tests.framework.env.EelsWithPython
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.getOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedTest
import org.junitpioneer.jupiter.cartesian.CartesianTest
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.time.Duration.Companion.minutes

/**
 * [ExecService.executeGetProcessForwardingPorts] makes a port on the machine of the process reachable from the IDE.
 * To exec this test against remote eels, you need `intellij.platform.ijent.testFramework` in classpath (exists on TC)
 */
@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
internal class ExecServicePortForwardingTest {
  @CartesianTest
  fun testForwardedPortIsReachable(
    @EelSource eelHolder: EelHolder,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val eel = eelHolder.eel

    // The server on the machine of the process is an eel acceptor, so the machine needs no server binary such as `nc`.
    val acceptor = openAcceptor(eel)
    val remotePort = acceptor.boundAddress.port
    val server = launch {
      for (connection in acceptor.incomingConnections) {
        connection.sendChannel.sendWholeText(HELLO)
        connection.sendChannel.close(null)
      }
    }

    // The process only has to run: the forwarding lives as long as the process.
    val (shell, _) = eel.exec.getShell()
    coroutineScope {
      val (process, forwardedPorts) = ExecService()
        .executeGetProcessForwardingPorts(BinOnEel(shell.asNioPath()), setOf(remotePort), scopeToBind = this)
        .getOrThrow()
      try {
        assertReceivesHello(forwardedPorts.getValue(remotePort))
      }
      finally {
        process.destroyForcibly()
      }
    }

    server.cancelAndJoin()
    acceptor.close()
  }

  /**
   * Here the process itself is the server, as a Jupyter kernel is.
   * On a remote eel the server is Python: [EelsWithPython] gives only remote eels that have it.
   * On the local eel a port is not forwarded, so a server in a coroutine of the test is sufficient, and Python is not necessary.
   *
   * [EelsWithPython] is on the method, not on the class: the eel provider reads the annotations of the method when there is one.
   */
  @ParameterizedTest
  @EelsWithPython
  fun testPortOfProcessIsReachable(eelHolder: EelHolder): Unit = timeoutRunBlocking(5.minutes) {
    val eel = eelHolder.eel
    if (eel.descriptor == LocalEelDescriptor) {
      val serverSocket = withContext(Dispatchers.IO) { ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)) }
      val server = launch(Dispatchers.IO) {
        serverSocket.use { it.accept().use { client -> client.getOutputStream().write(HELLO.encodeToByteArray()) } }
      }
      val port = serverSocket.localPort.toUShort()
      val (shell, _) = eel.exec.getShell()
      coroutineScope {
        val (process, forwardedPorts) = ExecService()
          .executeGetProcessForwardingPorts(BinOnEel(shell.asNioPath()), setOf(port), scopeToBind = this)
          .getOrThrow()
        try {
          assertReceivesHello(forwardedPorts.getValue(port))
        }
        finally {
          process.destroyForcibly()
        }
      }
      server.join()
      return@timeoutRunBlocking
    }

    val python = checkNotNull(SystemPythonService().findSystemPythons(eel).firstOrNull()) {
      "No Python on ${eel.descriptor.name}, but EelsWithPython expects one"
    }.pythonBinary
    // The ports to forward are known before the process starts, so the test takes a free port first.
    val port = openAcceptor(eel).let { acceptor ->
      try {
        acceptor.boundAddress.port
      }
      finally {
        acceptor.close()
      }
    }
    coroutineScope {
      val (process, forwardedPorts) = ExecService()
        .executeGetProcessForwardingPorts(BinOnEel(python), setOf(port), Args("-c", PYTHON_SERVER, port.toString()), scopeToBind = this)
        .getOrThrow()
      try {
        val firstLine = withContext(Dispatchers.IO) { process.inputStream.bufferedReader().readLine() }
        assertEquals(READY, firstLine, "The Python server did not start")
        assertReceivesHello(forwardedPorts.getValue(port))
      }
      finally {
        process.destroyForcibly()
      }
    }
  }

  private suspend fun assertReceivesHello(address: HostPort) {
    val received = withContext(Dispatchers.IO) {
      Socket(address.host, address.port).use { it.getInputStream().readAllBytes().decodeToString() }
    }
    assertEquals(HELLO, received, "Wrong data through the forwarded port $address")
  }

  private suspend fun openAcceptor(eel: EelApi): EelTunnelsApi.ConnectionAcceptor =
    eel.tunnels.getAcceptorForRemotePort()
      .hostAddress(EelTunnelsApi.HostAddress.Builder().hostname(LOOPBACK).build())
      .eelIt()

  private companion object {
    const val HELLO = "hello"
    const val READY = "ready"
    const val LOOPBACK = "127.0.0.1"

    /**
     * Listens on `127.0.0.1` and the port from the first argument, prints [READY], and sends [HELLO] to the first client.
     */
    const val PYTHON_SERVER =
      "import socket, sys; " +
      "s = socket.socket(); s.bind(('$LOOPBACK', int(sys.argv[1]))); s.listen(1); " +
      "print('$READY', flush=True); " +
      "c, _ = s.accept(); c.sendall(b'$HELLO'); c.close(); s.close()"
  }
}
