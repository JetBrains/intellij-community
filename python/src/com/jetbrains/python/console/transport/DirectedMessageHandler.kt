package com.jetbrains.python.console.transport

import com.jetbrains.python.console.transport.DirectedMessage.MessageDirection.REQUEST
import com.jetbrains.python.console.transport.DirectedMessage.MessageDirection.RESPONSE
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import java.io.OutputStream

internal class DirectedMessageHandler(private val responseStream: OutputStream,
                                      private val requestStream: OutputStream) : SimpleChannelInboundHandler<DirectedMessage>() {
  private var channel: Channel? = null

  private fun assureChannelPresent() = channel ?: throw IllegalStateException("`channel` must not be `null`")

  fun sendRequest(byteBuf: ByteBuf) {
    val channel = assureChannelPresent()
    val content = ByteArray(byteBuf.readableBytes())
    byteBuf.readBytes(content)
    channel.writeAndFlush(DirectedMessage(REQUEST, content))
  }

  override fun channelRegistered(ctx: ChannelHandlerContext) {
    channel = ctx.channel()
  }

  override fun channelRead0(ctx: ChannelHandlerContext, msg: DirectedMessage) {
    when (msg.direction) {
      RESPONSE -> responseStream
      REQUEST -> requestStream
    }.let {
      it.write(msg.content)
      it.flush()
    }
  }
}