package com.jetbrains.python.console.transport.server

import org.apache.thrift.TProcessor
import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.server.TThreadPoolServer
import org.apache.thrift.transport.TServerTransport
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class TNettyServer private constructor(transport: TServerTransport, processor: TProcessor,
                                       private val awaitTerminationImpl: (timeout: Long, unit: TimeUnit) -> Boolean) {
  private val server: TThreadPoolServer

  init {
    val args = TThreadPoolServer.Args(transport)
      .processor(processor)
      .protocolFactory(TBinaryProtocol.Factory())
      .stopTimeoutVal(1)

    server = TThreadPoolServer(args)
  }

  constructor(transport: TNettyServerTransport, processor: TProcessor) : this(transport, processor, { timeout: Long, unit: TimeUnit ->
    transport.awaitTermination(timeout, unit)
  })

  constructor(transport: TServerTransport, processor: TProcessor) : this(transport, processor, { _, _ -> true })

  fun serve() {
    server.serve()
  }

  fun stop(): Future<*> {
    server.stop()

    return object : Future<Void?> {
      override fun isDone(): Boolean = awaitTerminationImpl(0, TimeUnit.SECONDS)

      override fun get(): Void? {
        while (!awaitTerminationImpl(1, TimeUnit.MINUTES)) {
        }
        return null
      }

      @Throws(TimeoutException::class)
      override fun get(timeout: Long, unit: TimeUnit): Void? {
        if (!awaitTerminationImpl(timeout, unit)) {
          throw TimeoutException()
        }
        else {
          return null
        }
      }

      override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false

      override fun isCancelled(): Boolean = false
    }
  }
}