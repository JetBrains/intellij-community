package com.jetbrains.python.console

import org.apache.thrift.async.AsyncMethodCallback
import org.apache.thrift.async.TAsyncClientManager
import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.server.TNonblockingServer
import org.apache.thrift.server.TServer
import org.apache.thrift.server.TSimpleServer
import org.apache.thrift.server.TThreadedSelectorServer
import org.apache.thrift.transport.*
import java.lang.Exception
import java.util.concurrent.atomic.AtomicInteger

const val ASYNC_CLIENT = true

fun main(args: Array<String>) {
  // let's try to start IDE and PythonConsole on the same connection

  val serverPort = 9090

  if (ASYNC_CLIENT) {
    startNonblockingServer(serverPort)
  }
  else {
    startServer(serverPort)
  }

  Thread.sleep(1000L)

  // sync client (`IDE.Client`) expects to receive the result in the order of requests

  if (ASYNC_CLIENT) {
    startAsyncClient(serverPort)
  }
  else {
    startSyncClient(serverPort)
  }
}

private fun startServer(serverPort: Int) {
  val serverTransport: TServerTransport = TServerSocket(serverPort)
  val handler: IDE.Iface = IDEHandler()
  val processor: IDE.Processor<IDE.Iface> = IDE.Processor(handler)
  val server: TServer = TSimpleServer(TServer.Args(serverTransport).processor(processor))
  Thread { server.serve() }.start()
}

private fun startNonblockingServer(serverPort: Int) {
  val serverTransport: TNonblockingServerTransport = TNonblockingServerSocket(serverPort)
  val handler: IDE.Iface = IDEHandler()
  val processor: IDE.Processor<IDE.Iface> = IDE.Processor(handler)
  val server: TServer
  if (true) {
    server = TThreadedSelectorServer(TThreadedSelectorServer.Args(serverTransport).processor(processor))
  } else {
    server = TNonblockingServer(TNonblockingServer.Args(serverTransport).processor(processor))
  }
  Thread { server.serve() }.start()
}

private fun startSyncClient(serverPort: Int) {
  // we don't need special protocol for the bidirectional communication... do we?

  val transport: TTransport = TSocket("localhost", serverPort)
  transport.open()
  try {
    val protocol = TBinaryProtocol(transport)
    val client = IDE.Client(protocol)
    val input = client.requestInput("/test")
    println("User input: $input")
  }
  finally {
    transport.close()
  }
}

private fun startAsyncClient(serverPort: Int) {
  // we don't need special protocol for the bidirectional communication... do we?

  // `TAsyncClient` does not support simultaneous method calls

  val nonblockingTransport: TNonblockingTransport = TNonblockingSocket("localhost", serverPort)
  try {
    val asyncClientManager = TAsyncClientManager()
    val client = IDE.AsyncClient(TBinaryProtocol.Factory(), asyncClientManager, nonblockingTransport)
    val callback = object : AsyncMethodCallback<String?> {
      override fun onComplete(p0: String?) {
        println("User input: $p0")
      }

      override fun onError(p0: Exception?) {
        p0?.printStackTrace()
      }
    }
    client.requestInput("/test", callback)
    client.requestInput("/test", callback)
  }
  finally {
  }
}

class IDEHandler : IDE.Iface {
  override fun IPythonEditor(path: String?, line: String?): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  private val requestNumber = AtomicInteger(0)

  override fun notifyFinished(needsMoreInput: Boolean) {
    println("TODO process finished")
  }

  override fun requestInput(path: String?): String {
    val result = requestNumber.getAndIncrement()
    if (result % 2 == 0) {
      Thread.sleep(10000L)
    }
    return "$result"
  }

  override fun notifyAboutMagic(commands: MutableList<String>?, isAutoMagic: Boolean) {
    println("Notified about magic commands=$commands, isAutoMagic=$isAutoMagic")
  }

  override fun showConsole() {
    println("TODO show console")
  }

  override fun returnFullValue(requestSeq: Int, response: MutableList<DebugValue>?) {
    println("full value for requestSeq=$requestSeq is response=$response")
  }
}