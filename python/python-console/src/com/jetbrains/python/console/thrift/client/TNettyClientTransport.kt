package com.jetbrains.python.console.thrift.client

import com.jetbrains.python.console.thrift.DirectedMessage
import com.jetbrains.python.console.thrift.DirectedMessageCodec
import com.jetbrains.python.console.thrift.TCumulativeTransport
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import org.apache.thrift.transport.TServerTransport
import org.apache.thrift.transport.TTransport
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class TNettyClientTransport(private val host: String,
                            private val port: Int) : TTransport() {
  private var channel: Channel? = null

  private var messageHandler: DirectedMessageHandler? = null

  private val responseStream: PipedOutputStream = PipedOutputStream()

  private val responseInputStream: PipedInputStream = PipedInputStream(responseStream)

  /**
   * "Client as server" accepted transport.
   */
  private val serverAcceptedTransport = TNettyTransport()

  val serverTransport: TServerTransport = object : TServerTransport() {
    private val acceptedOnce = AtomicBoolean(false)

    override fun listen() {}

    override fun acceptImpl(): TTransport {
      if (acceptedOnce.compareAndSet(false, true)) {
        return serverAcceptedTransport
      }

      // waits forever for the consequent `accept()` calls
      // in `org.apache.thrift.server.TThreadPoolServer.serve`
      val lock = Object()
      while (true) {
        synchronized(lock) {
          lock.wait()
        }
      }
    }

    override fun close() {}
  }

  private fun assureChannelPresent(): Channel = channel ?: throw IllegalStateException("`channel` is `null`")

  private val messageBuffer: ByteBuf = Unpooled.buffer()

  override fun open() {
    val workerGroup = NioEventLoopGroup()

    try {
      val b = Bootstrap() // (1)
      b.group(workerGroup) // (2)
      b.channel(NioSocketChannel::class.java) // (3)
      b.option(ChannelOption.SO_KEEPALIVE, true) // (4)
      b.handler(object : ChannelInitializer<SocketChannel>() {
        override fun initChannel(ch: SocketChannel) {
          ch.pipeline().addLast(LoggingHandler(LogLevel.DEBUG))

          ch.pipeline().addLast(LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4))
          ch.pipeline().addLast(LengthFieldPrepender(4))

          ch.pipeline().addLast(DirectedMessageCodec())
          ch.pipeline().addLast(DirectedMessageHandler(responseStream, serverAcceptedTransport.outputStream))
        }
      })

      // Start the client.
      val f = b.connect(host, port).sync() // (5)

      channel = f.channel()

      messageHandler = f.channel().pipeline().get(DirectedMessageHandler::class.java)

      /*
            // Wait until the connection is closed.
            f.channel().closeFuture().sync()
      */
    }
    finally {
      /*
            workerGroup.shutdownGracefully()
      */
    }
  }

  override fun write(buf: ByteArray, off: Int, len: Int) {
    messageBuffer.writeBytes(buf, off, len)
  }

  /**
   * Sends the request to the server with the buffer content.
   *
   * Note that the buffer might contain arbitrary portion of consecutive
   * bytes of the request message or messages.
   */
  override fun flush() {
    try {
      messageHandler?.sendRequest(messageBuffer)
    }
    finally {
      messageBuffer.clear()
    }
  }

  override fun isOpen(): Boolean = channel?.isOpen == true

  override fun close() {
    // Wait until the connection is closed.
    assureChannelPresent().closeFuture().sync()
  }

  override fun read(buf: ByteArray, off: Int, len: Int): Int = responseInputStream.read(buf, off, len)

  private fun getChannel(): Channel = channel ?: throw IllegalStateException("`channel` must not be `null`")

  private inner class TNettyTransport : TCumulativeTransport() {
    override fun isOpen(): Boolean = channel?.isOpen == true

    override fun writeMessage(content: ByteArray) {
      getChannel().writeAndFlush(DirectedMessage(DirectedMessage.MessageDirection.RESPONSE, content))
    }
  }
}