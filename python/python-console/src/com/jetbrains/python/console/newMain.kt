package com.jetbrains.python.console

import com.jetbrains.python.console.thrift.client.TNettyClientTransport
import com.jetbrains.python.console.thrift.server.TNettyServerTransport
import org.apache.thrift.protocol.TJSONProtocol
import org.apache.thrift.server.TServer
import org.apache.thrift.server.TSimpleServer

/**
 * @author Alexander Koshevoy
 */
fun main(args: Array<String>) {
  org.apache.log4j.BasicConfigurator.configure()

  val serverPort = 9090

  // let's try to start IDE and PythonConsole on the same connection

  startSyncServer(serverPort)

  Thread.sleep(1000L)

  startSyncClient(serverPort)
}

private fun startSyncServer(serverPort: Int) {
  val serverTransport = TNettyServerTransport(serverPort)
  val handler: IDE.Iface = object : IDE.Iface {
    override fun notifyFinished(needsMoreInput: Boolean) {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun requestInput(path: String): String = "Hello!"

    override fun notifyAboutMagic(commands: MutableList<String>?, isAutoMagic: Boolean) {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun showConsole() {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun returnFullValue(requestSeq: Int, response: MutableList<DebugValue>?) {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

  }
  val processor: IDE.Processor<IDE.Iface> = IDE.Processor(handler)
  val server: TServer = TSimpleServer(TServer.Args(serverTransport).processor(processor).protocolFactory(TJSONProtocol.Factory()))
  Thread { server.serve() }.start()

  // server as client

  Thread {
    val clientTransport = serverTransport.getReverseTransport()
    clientTransport.open()
    try {
      val protocol = TJSONProtocol(clientTransport)
      val client = PythonConsole.Client(protocol)
      val description = client.getDescription("bu")
      println("Description: $description")
    }
    finally {
      clientTransport.close()
    }
  }.start()
}

private fun startSyncClient(serverPort: Int) {
  val clientTransport = TNettyClientTransport("localhost", serverPort)
  clientTransport.open()
  Thread {
    try {
      val protocol = TJSONProtocol(clientTransport)
      val client = IDE.Client(protocol)

      Thread {
        val input = client.requestInput("/test")
        println("User input: $input")
      }.start()
    }
    finally {
      clientTransport.close()
    }
  }.start()

  // client as server

  val serverTransport = clientTransport.serverTransport

  val handler: PythonConsole.Iface = object : PythonConsole.Iface {
    override fun execLine(line: String?): Boolean {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun execMultipleLines(lines: String?): Boolean {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getCompletions(text: String?, actTok: String?): MutableList<CompletionOption> {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getDescription(text: String?): String {
      return "this is description for $text"
    }

    override fun getFrame(): MutableList<DebugValue> {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getVariable(variable: String?): MutableList<DebugValue> {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun changeVariable(evaluationExpression: String?, value: String?) {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun connectToDebugger(localPort: Int, opts: MutableMap<String, String>?) {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun handshake(): String {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun close() {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun evaluate(expression: String?): MutableList<DebugValue> {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getArray(vars: String?, rowOffset: Int, colOffset: Int, rows: Int, cols: Int, format: String?): GetArrayResponse {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun loadFullValue(variables: MutableList<String>?): Int {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

  }

  val processor: PythonConsole.Processor<PythonConsole.Iface> = PythonConsole.Processor(handler)
  val server: TServer = TSimpleServer(TServer.Args(serverTransport).processor(processor).protocolFactory(TJSONProtocol.Factory()))
  Thread { server.serve() }.start()
}
