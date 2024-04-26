package com.jetbrains.python.console.transport.server

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ConcurrencyUtil
import com.jetbrains.python.console.transport.DirectedMessage
import com.jetbrains.python.console.transport.DirectedMessageCodec
import com.jetbrains.python.console.transport.DirectedMessageHandler
import com.jetbrains.python.console.transport.TCumulativeTransport
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.logging.LoggingHandler
import org.apache.thrift.TConfiguration
import org.apache.thrift.transport.TServerTransport
import org.apache.thrift.transport.TTransport
import org.apache.thrift.transport.TTransportException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @param host the hostname to bind Python Console server at
 * @param port the port to bind Python Console server at
 */
class TNettyServerTransport(host: String, port: Int) : TServerTransport() {
  private val nettyServer: NettyServer = NettyServer(host, port)

  @Throws(TTransportException::class)
  override fun listen() {
    try {
      nettyServer.listen()
    }
    catch (e: InterruptedException) {
      throw TTransportException(e)
    }
  }

  @Throws(InterruptedException::class)
  fun waitForBind() {
    nettyServer.waitForBind()
  }

  @Throws(InterruptedException::class)
  fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
    return nettyServer.awaitTermination(timeout, unit)
  }

  override fun accept(): TTransport = nettyServer.accept()

  override fun interrupt() {
    close()
  }

  override fun close() {
    nettyServer.close()
  }

  @Throws(InterruptedException::class)
  fun getReverseTransport(): TTransport = nettyServer.takeReverseTransport()

  private class NettyServer(val host: String, val port: Int) {
    private val closed: AtomicBoolean = AtomicBoolean(false)

    private val acceptQueue: BlockingQueue<TTransport> = LinkedBlockingQueue()
    private val reverseTransportQueue: BlockingQueue<TTransport> = LinkedBlockingQueue()

    // The first one, often called 'boss', accepts an incoming connection.
    private val bossGroup = NioEventLoopGroup(0, ConcurrencyUtil.newNamedThreadFactory("Python Console NIO Event Loop Boss")) // (1)

    // The second one, often called 'worker', handles the traffic of the
    // accepted connection once the boss accepts the connection and registers
    // the accepted connection to the worker.
    private val workerGroup = NioEventLoopGroup(0, ConcurrencyUtil.newNamedThreadFactory("Python Console NIO Event Loop Worker"))

    private val serverBound = CountDownLatch(1)

    fun listen() {
      // ServerBootstrap is a helper class that sets up a server. You can set
      // up the server using a Channel directly. However, please note that this
      // is a tedious process, and you do not need to do that in most cases.
      val b = ServerBootstrap() // (2)
      b.group(bossGroup, workerGroup)
        // Here, we specify to use the NioServerSocketChannel class which is
        // used to instantiate a new Channel to accept incoming connections.
        .channel(NioServerSocketChannel::class.java) // (3)
        // The handler specified here will always be evaluated by a newly
        // accepted Channel. The ChannelInitializer is a special handler that
        // is purposed to help a user configure a new Channel. It is most
        // likely that you want to configure the ChannelPipeline of the new
        // Channel by adding some handlers such as DiscardServerHandler to
        // implement your network application. As the application gets
        // complicated, it is likely that you will add more handlers to the
        // pipeline and extract this anonymous class into a top level class
        // eventually.
        .childHandler(object : ChannelInitializer<SocketChannel>() { // (4)
          @Throws(Exception::class)
          override fun initChannel(ch: SocketChannel) {
            LOG.debug("Connection to Thrift server on $port received")

            ch.pipeline().addLast(LoggingHandler("#${TNettyServerTransport::class.java.name}"))

            // `FixedLengthFrameDecoder` is excessive but convenient
            ch.pipeline().addLast(LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4))
            ch.pipeline().addLast(LengthFieldPrepender(4))
            // `DirectedMessageCodec` is above Thrift messages
            ch.pipeline().addLast(DirectedMessageCodec())

            // here comes Thrift
            val thriftTransport = TNettyTransport(ch)
            val reverseTransport = TNettyClientTransport(ch)

            ch.pipeline().addLast(DirectedMessageHandler(reverseTransport.outputStream, thriftTransport.outputStream))

            ch.pipeline().addLast(object : ChannelInboundHandlerAdapter() {
              override fun channelInactive(ctx: ChannelHandlerContext) {
                thriftTransport.close()
                reverseTransport.close()

                super.channelInactive(ctx)
              }
            })

            // ready!
            acceptQueue.put(thriftTransport)
            reverseTransportQueue.put(reverseTransport)
          }
        })
        // You can also set the parameters which are specific to the Channel
        // implementation. We are writing a TCP/IP server, so we are allowed to
        // set the socket options such as tcpNoDelay and keepAlive. Please
        // refer to the apidocs of ChannelOption and the specific ChannelConfig
        // implementations to get an overview about the supported
        // ChannelOptions.
        .option(ChannelOption.SO_BACKLOG, 128)          // (5)
        // Did you notice option() and childOption()? option() is for the
        // NioServerSocketChannel that accepts incoming connections.
        // childOption() is for the Channels accepted by the parent
        // ServerChannel, which is NioServerSocketChannel in this case.
        .childOption(ChannelOption.SO_KEEPALIVE, true) // (6)

      // Bind and start to accept incoming connections.
      // We are ready to go now. What's left is to bind to the port and to
      // start the server.
      b.bind(host, port).sync() // (7)

      LOG.debug("Running Netty server on $port")

      serverBound.countDown()
    }

    /**
     * @throws InterruptedException if [CountDownLatch.await] is interrupted
     * @throws ServerClosedException if [NettyServer] gets closed
     */
    @Throws(InterruptedException::class)
    fun waitForBind() {
      while (!closed.get()) {
        if (serverBound.await(100L, TimeUnit.MILLISECONDS)) {
          return
        }
      }
      throw ServerClosedException()
    }

    fun accept(): TTransport {
      try {
        while (true) {
          val acceptedTransport = acceptQueue.poll(100L, TimeUnit.MILLISECONDS)
          if (closed.get()) {
            throw TTransportException("Netty server is closed")
          }
          if (acceptedTransport != null) {
            return acceptedTransport
          }
        }
      }
      catch (e: InterruptedException) {
        throw TTransportException(e)
      }
    }

    /**
     * @throws InterruptedException if [BlockingQueue.poll] is interrupted
     * @throws ServerClosedException if [NettyServer] gets closed
     */
    @Throws(InterruptedException::class)
    fun takeReverseTransport(): TTransport {
      while (!closed.get()) {
        val element = reverseTransportQueue.poll(100L, TimeUnit.MILLISECONDS)
        if (element != null) {
          return element
        }
      }
      throw ServerClosedException()
    }

    /**
     * Shutdown the server [NioEventLoopGroup].
     *
     * Does not wait for the server socket to be closed.
     */
    fun close() {
      if (closed.compareAndSet(false, true)) {
        LOG.debug("Closing Netty server")

        workerGroup.shutdownGracefully()
        bossGroup.shutdownGracefully()
      }
    }

    @Throws(InterruptedException::class)
    fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
      return workerGroup.awaitTermination(timeout, unit) && bossGroup.awaitTermination(timeout, unit)
    }
  }

  private class TNettyTransport(private val channel: SocketChannel) : TCumulativeTransport() {
    override fun isOpen(): Boolean = channel.isOpen

    override fun writeMessage(content: ByteArray) {
      channel.writeAndFlush(DirectedMessage(DirectedMessage.MessageDirection.RESPONSE, content))
    }

    /**
     * Workarounds the problem of the original method where the generic [TTransportException] is thrown even if the client side has properly
     * finished its work. The generic exceptions are not ignored in [org.apache.thrift.server.TThreadPoolServer.WorkerProcess.run], these
     * exceptions are rethrown and they are shown in as the error in the IDE.
     *
     * The workaround is to throw [TTransportException] of the specific type [TTransportException.END_OF_FILE], which is ignored by the said
     * `run` method.
     */
    override fun readAll(buf: ByteArray, off: Int, len: Int): Int {
      var got = 0
      while (got < len) {
        val ret = read(buf, off + got, len - got)
        if (ret <= 0) {
          if (got == 0) {
            // properly handle the case when the client seems to have finished its working session normally
            throw TTransportException(TTransportException.END_OF_FILE)
          }
          else {
            throw TTransportException(
              "Cannot read. Remote side has closed. Tried to read "
              + len
              + " bytes, but only got "
              + got
              + " bytes. (This is often indicative of an internal error on the server side. Please check your server logs.)")
          }
        }
        got += ret
      }
      return got
    }

    override fun getConfiguration(): TConfiguration? = null

    override fun updateKnownMessageSize(size: Long) {}

    override fun checkReadBytesAvailable(numBytes: Long) {}
  }

  private class TNettyClientTransport(private val channel: SocketChannel) : TCumulativeTransport() {
    override fun isOpen(): Boolean = channel.isOpen
    override fun getConfiguration(): TConfiguration? = null

    override fun updateKnownMessageSize(size: Long) {}

    override fun checkReadBytesAvailable(numBytes: Long) {}

    override fun writeMessage(content: ByteArray) {
      channel.writeAndFlush(DirectedMessage(DirectedMessage.MessageDirection.REQUEST, content))
    }
  }

  companion object {
    val LOG = Logger.getInstance(TNettyServerTransport::class.java)
  }
}