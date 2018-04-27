package com.jetbrains.python.console.thrift.server

import com.jetbrains.python.console.thrift.DirectedMessage
import com.jetbrains.python.console.thrift.DirectedMessageCodec
import com.jetbrains.python.console.thrift.TNettyCumulativeTransport
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.logging.LoggingHandler
import org.apache.thrift.transport.TServerTransport
import org.apache.thrift.transport.TTransport
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

/**
 *
 */
class TNettyServerTransport(port: Int) : TServerTransport() {
  private val nettyServer: NettyServer = NettyServer(port)

  override fun listen() {
    nettyServer.listen()
  }

  override fun acceptImpl(): TTransport = nettyServer.accept()

  override fun close() {
    nettyServer.close()
  }

  fun getReverseTransport(): TTransport = nettyServer.takeReverseTransport()

  private class NettyServer(val port: Int) {
    private val acceptQueue: BlockingQueue<TTransport> = LinkedBlockingQueue()
    private val reverseTransportQueue: BlockingQueue<TTransport> = LinkedBlockingQueue()

    // The first one, often called 'boss', accepts an incoming connection.
    private val bossGroup = NioEventLoopGroup() // (1)

    // The second one, often called 'worker', handles the traffic of the
    // accepted connection once the boss accepts the connection and registers
    // the accepted connection to the worker.
    private val workerGroup = NioEventLoopGroup()

    fun listen() {
      // TODO check state!

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
            ch.pipeline().addLast(LoggingHandler())

            // `FixedLengthFrameDecoder` is excessive but convenient
            ch.pipeline().addLast(LengthFieldBasedFrameDecoder(1024, 0, 4, 0, 4))
            ch.pipeline().addLast(LengthFieldPrepender(4))
            // `DirectedMessageCodec` is above Thrift messages
            ch.pipeline().addLast(DirectedMessageCodec())

            // here comes Thrift
            val thriftTransport = TNettyTransport(ch)
            val reverseTransport = TNettyClientTransport(ch)

            ch.pipeline().addLast(object : SimpleChannelInboundHandler<DirectedMessage>() {
              override fun channelRead0(ctx: ChannelHandlerContext, msg: DirectedMessage) {
                when (msg.direction) {
                  DirectedMessage.MessageDirection.REQUEST -> {
                    thriftTransport.outputStream
                  }
                  DirectedMessage.MessageDirection.RESPONSE -> {
                    reverseTransport.outputStream
                  }
                }.let {
                  it.write(msg.content)
                  it.flush()
                }
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
      // start the server. Here, we bind to the port 8080 of all NICs (network
      // interface cards) in the machine. You can now call the bind() method as
      // many times as you want (with different bind addresses.)
      val f = b.bind(port).sync() // (7)

      // TODO move to `close()`
      /*
            // Wait until the server socket is closed.
            // In this example, this does not happen, but you can do that to gracefully
            // shut down your server.
            f.channel().closeFuture().sync()
      */
    }

    fun accept(): TTransport = acceptQueue.take()

    fun takeReverseTransport(): TTransport = reverseTransportQueue.take()

    fun close() {
      workerGroup.shutdownGracefully()
      bossGroup.shutdownGracefully()

      // TODO close server channel!
    }
  }

  private class TNettyTransport(private val channel: SocketChannel) : TNettyCumulativeTransport() {
    override fun isOpen(): Boolean = channel.isOpen

    override fun writeMessage(content: ByteArray) {
      channel.writeAndFlush(DirectedMessage(DirectedMessage.MessageDirection.RESPONSE, content))
    }
  }

  private class TNettyClientTransport(private val channel: SocketChannel) : TNettyCumulativeTransport() {
    override fun isOpen(): Boolean = channel.isOpen

    override fun writeMessage(content: ByteArray) {
      channel.writeAndFlush(DirectedMessage(DirectedMessage.MessageDirection.REQUEST, content))
    }
  }
}