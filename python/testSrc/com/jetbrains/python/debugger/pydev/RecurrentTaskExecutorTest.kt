// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(Parameterized::class)
class RecurrentTaskExecutorTest(private val requestsNumber: Int, private val delayInMillis: Long) {
  @Test
  fun allConnectionsEstablished() {
    val connectionQueue = LinkedBlockingQueue<EstablishedConnection>()
    val connectedToAllLatch = CountDownLatch(requestsNumber)
    val successfulConnections = AtomicInteger(0)
    val executor = RecurrentTaskExecutor(threadsName = "RecurrentTaskExecutorTest.emptyTasks()",
                                         recurrentTask = ConnectionTask(connectionQueue = connectionQueue, delayInMillis = delayInMillis),
                                         callback = CountingCallback(connectedToAllLatch, successfulConnections))
    try {
      createConnectionsThread(connectionQueue, requestsNumber).start()
      createRequestsThread(executor, requestsNumber).start()

      val allConnectionsEstablished = connectedToAllLatch.await(10, TimeUnit.SECONDS)

      assertTrue(allConnectionsEstablished)
      assertEquals(requestsNumber, successfulConnections.get())
    }
    finally {
      executor.dispose()
    }
  }

  /**
   * Request connections for the [RecurrentTaskExecutor].
   */
  private fun createRequestsThread(executor: RecurrentTaskExecutor<EstablishedConnection>, requestsNumber: Int): Thread {
    return Thread(Runnable { repeat(times = requestsNumber, action = { executor.incrementRequests() }) })
  }

  /**
   * Put connections for the [RecurrentTaskExecutor] in the queue.
   */
  private fun createConnectionsThread(connectionQueue: BlockingQueue<EstablishedConnection>, requestsNumber: Int): Thread {
    return Thread(Runnable { repeat(times = requestsNumber, action = { connectionQueue.put(EstablishedConnection()) }) })
  }

  private class CountingCallback(val connectedLatch: CountDownLatch,
                                 val successCounter: AtomicInteger) : RecurrentTaskExecutor.Callback<Any> {
    override fun onSuccess(result: Any) {
      successCounter.incrementAndGet()
      connectedLatch.countDown()
    }
  }

  private class ConnectionTask(val connectionQueue: BlockingQueue<EstablishedConnection>, val delayInMillis: Long = 0L)
    : RecurrentTaskExecutor.RecurrentTask<EstablishedConnection> {
    override fun tryToPerform(): EstablishedConnection {
      Thread.sleep(delayInMillis)

      return connectionQueue.take()
    }
  }

  /**
   * Represents the connection object. This class is just for the better
   * readability of the test.
   */
  private class EstablishedConnection

  companion object {
    @JvmStatic
    @Parameters(name = "{index}: connections={0}, connection delay={1} ms")
    fun data(): Collection<Array<Any>> = listOf<Array<Any>>(arrayOf(100, 0L), arrayOf(40, 50L), arrayOf(20, 100L))
  }
}