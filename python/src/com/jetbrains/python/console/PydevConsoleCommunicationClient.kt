// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.Project
import com.jetbrains.python.console.protocol.PythonConsoleBackendService
import com.jetbrains.python.console.protocol.PythonConsoleFrontendService
import com.jetbrains.python.console.transport.client.TNettyClientTransport
import com.jetbrains.python.console.transport.server.TNettyServer
import com.jetbrains.python.debugger.PyDebugValueExecutionService
import org.apache.thrift.protocol.TBinaryProtocol
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * This is [PydevConsoleCommunication] where Python Console backend acts like a
 * server and IDE acts like a client.
 *
 * Python Console [Process] is expected to be already started. It is passed as
 * [_pythonConsoleProcess] property.
 */
class PydevConsoleCommunicationClient(project: Project,
                                      private val host: String, private val port: Int,
                                      private val _pythonConsoleProcess: Process) : PydevConsoleCommunication(project) {
  private var server: TNettyServer? = null

  /**
   * Thrift RPC client for sending messages to the server.
   *
   * Guarded by [stateLock].
   */
  private var client: PythonConsoleBackendServiceDisposable? = null

  private val clientTransport: TNettyClientTransport = TNettyClientTransport(host, port)

  private val stateLock: Lock = ReentrantLock()
  private val stateChanged: Condition = stateLock.newCondition()

  /**
   * Initial non-thread safe [PythonConsoleBackendService.Client].
   *
   * Guarded by [stateLock].
   */
  private var initialPythonConsoleClient: PythonConsoleBackendService.Iface? = null

  /**
   * Guarded by [stateLock].
   */
  private var isClosed = false

  /**
   * Establishes connection to Python Console backend listening at
   * [host]:[port].
   */
  fun connect() {
    ApplicationManager.getApplication().executeOnPooledThread {
      // TODO handle exception scenario
      clientTransport.open()

      val clientProtocol = TBinaryProtocol(clientTransport)
      val client = PythonConsoleBackendService.Client(clientProtocol)

      val serverTransport = clientTransport.serverTransport

      val serverHandler = createPythonConsoleFrontendHandler()
      val serverProcessor = PythonConsoleFrontendService.Processor<PythonConsoleFrontendService.Iface>(serverHandler)

      val server = TNettyServer(serverTransport, serverProcessor)

      stateLock.withLock {
        if (isClosed) throw ProcessCanceledException()

        this.server = server
        initialPythonConsoleClient = client

        stateChanged.signalAll()
      }

      ApplicationManager.getApplication().executeOnPooledThread { server.serve() }

      val executionService = PyDebugValueExecutionService.getInstance(myProject)
      executionService.sessionStarted(this)
      addFrameListener { executionService.cancelSubmittedTasks(this@PydevConsoleCommunicationClient) }
    }
  }

  override fun getPythonConsoleBackendClient(): PythonConsoleBackendServiceDisposable {
    stateLock.withLock {
      while (!isClosed && _pythonConsoleProcess.isAlive) {
        // if `client` is set just return it
        client?.let {
          return it
        }

        val initialPythonConsoleClient = initialPythonConsoleClient

        if (initialPythonConsoleClient != null) {
          val newClient = synchronizedPythonConsoleClient(PydevConsoleCommunication::class.java.classLoader,
                                                          initialPythonConsoleClient, _pythonConsoleProcess)
          client = newClient
          return newClient
        }
        else {
          stateChanged.await()
        }
      }
      if (!_pythonConsoleProcess.isAlive) {
        throw PyConsoleProcessFinishedException(_pythonConsoleProcess.exitValue())
      }
      throw CommunicationClosedException()
    }
  }

  override fun closeCommunication(): Future<*> {
    stateLock.withLock {
      try {
        isClosed = true
      }
      finally {
        stateChanged.signalAll()
      }
    }

    // `client` cannot be assigned after `isClosed` is set

    val progressIndicator: ProgressIndicator? = ProgressIndicatorProvider.getInstance().progressIndicator

    // if client exists then try to gracefully `close()` it
    try {
      client?.apply {
        progressIndicator?.text2 = "Sending close message to Python Console..."

        close()
        dispose()
      }
    }
    catch (e: Exception) {
      // ignore exceptions on `client` shutdown
    }

    _pythonConsoleProcess.let {
      progressIndicator?.text2 = "Waiting for Python Console process to finish..."

      // TODO move under the future!
      try {
        do {
          progressIndicator?.checkCanceled()
        }
        while (!it.waitFor(500, TimeUnit.MILLISECONDS))
      }
      catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
      }
    }


    // explicitly close Netty client
    clientTransport.close()

    // we know that in this case `server.stop()` would do almost nothing
    return server?.stop() ?: CompletableFuture.completedFuture(null)
  }

  override fun isCommunicationClosed(): Boolean = stateLock.withLock { isClosed }
}