package com.jetbrains.python.console.thrift

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageCodec

internal class DirectedMessageCodec : ByteToMessageCodec<DirectedMessage>() {
  override fun encode(ctx: ChannelHandlerContext, msg: DirectedMessage, out: ByteBuf) {
    out.writeByte(msg.direction.ordinal)
    out.writeBytes(msg.content)
  }

  override fun decode(ctx: ChannelHandlerContext, `in`: ByteBuf, out: MutableList<Any>) {
    val directionByte = `in`.readByte()
    val direction = DirectedMessage.MessageDirection.values()[directionByte.toInt()]
    val length = `in`.readableBytes()
    val content = ByteArray(length)
    `in`.readBytes(content)
    out.add(DirectedMessage(direction, content))

    // `in` will be released later in `ByteToMessageDecoder.channelRead()`
  }
}