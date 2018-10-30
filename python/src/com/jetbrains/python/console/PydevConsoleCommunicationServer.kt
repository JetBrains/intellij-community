// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.Project
import com.jetbrains.python.console.protocol.PythonConsoleBackendService
import com.jetbrains.python.console.protocol.PythonConsoleFrontendService
import com.jetbrains.python.console.transport.server.ServerClosedException
import com.jetbrains.python.console.transport.server.TNettyServer
import com.jetbrains.python.console.transport.server.TNettyServerTransport
import com.jetbrains.python.debugger.PyDebugValueExecutionService
import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.transport.TTransport
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PydevConsoleCommunicationServer(project: Project, port: Int) : PydevConsoleCommunication(project) {
  private val serverTransport: TNettyServerTransport

  /**
   * This is the server responsible for giving input to a raw_input() requested.
   */
  private val server: TNettyServer

  /**
   * Thrift RPC client for sending messages to the server.
   */
  private var client: PythonConsoleBackendServiceDisposable? = null

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
  private var _pythonConsoleProcess: Process? = null

  /**
   * Guarded by [stateLock].
   */
  private var isFailedOnBound: Boolean = false

  /**
   * Guarded by [stateLock].
   */
  private var isServerBound: Boolean = false

  /**
   * Guarded by [stateLock].
   */
  private var isClosed: Boolean = false

  init {
    val serverHandler = createPythonConsoleFrontendHandler()
    val serverProcessor = PythonConsoleFrontendService.Processor<PythonConsoleFrontendService.Iface>(serverHandler)
    //noinspection IOResourceOpenedButNotSafelyClosed
    serverTransport = TNettyServerTransport(port)
    server = TNettyServer(serverTransport, serverProcessor)
  }

  /**
   * Must be called once.
   */
  fun serve() {
    // start server in the separate thread
    ApplicationManager.getApplication().executeOnPooledThread { server.serve() }

    ApplicationManager.getApplication().executeOnPooledThread {
      // this will wait for the connection of Python Console to the IDE
      val clientTransport: TTransport
      try {
        clientTransport = serverTransport.getReverseTransport()
      }
      catch (e: ServerClosedException) {
        // this is the normal execution flow
        throw ProcessCanceledException(e)
      }

      val clientProtocol = TBinaryProtocol(clientTransport)
      val client = PythonConsoleBackendService.Client(clientProtocol)

      stateLock.withLock {
        // early close
        if (isClosed) {
          server.stop()

          // this is the normal execution flow
          throw ProcessCanceledException()
        }

        initialPythonConsoleClient = client

        stateChanged.signalAll()
      }

      val executionService = PyDebugValueExecutionService.getInstance(myProject)
      executionService.sessionStarted(this)
      addFrameListener { executionService.cancelSubmittedTasks(this@PydevConsoleCommunicationServer) }
    }

    stateLock.withLock {
      try {
        // waiting on `CountDownLatch.await()` within `stateLock` might be harmful
        serverTransport.waitForBind()
        isServerBound = true
      }
      finally {
        isFailedOnBound = !isServerBound

        stateChanged.signalAll()
      }
    }
  }

  /**
   * The Python Console process is expected to be set after the server is
   * bound.
   */
  fun setPythonConsoleProcess(pythonConsoleProcess: Process) {
    stateLock.withLock {
      if (isClosed) {
        throw CommunicationClosedException()
      }

      if (!isServerBound) {
        LOG.warn("Python Console process is set before IDE server is bound, the process may not be able to connect to the server")
      }

      _pythonConsoleProcess = pythonConsoleProcess

      stateChanged.signalAll()
    }
  }


  override fun getPythonConsoleBackendClient(): PythonConsoleBackendServiceDisposable {
    stateLock.withLock {
      while (!isClosed && !isFailedOnBound) {
        // if `client` is set just return it
        client?.let {
          return it
        }

        val initialPythonConsoleClient = initialPythonConsoleClient
        val pythonConsoleProcess = _pythonConsoleProcess

        if (initialPythonConsoleClient != null && pythonConsoleProcess != null) {
          val newClient = synchronizedPythonConsoleClient(PydevConsoleCommunication::class.java.classLoader,
                                                          initialPythonConsoleClient, pythonConsoleProcess)
          client = newClient
          return newClient
        }
        else {
          stateChanged.await()
        }
      }
      throw CommunicationClosedException()
    }
  }

  override fun closeCommunication(): Future<*> {
    val progressIndicator: ProgressIndicator? = ProgressIndicatorProvider.getInstance().progressIndicator

    stateLock.withLock {
      try {
        isClosed = true
      }
      finally {
        stateChanged.signalAll()
      }
    }

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

    _pythonConsoleProcess?.let {
      progressIndicator?.text2 = "Waiting for Python Console process to finish..."

      // TODO move under feature!
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

    return server.stop()
  }

  override fun isCommunicationClosed(): Boolean = stateLock.withLock { isClosed }

  companion object {
    val LOG: Logger = Logger.getInstance(PydevConsoleCommunicationServer::class.java)
  }
}