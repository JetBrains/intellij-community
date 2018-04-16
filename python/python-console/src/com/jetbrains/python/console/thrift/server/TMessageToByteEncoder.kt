package com.jetbrains.python.console.thrift.server

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import org.apache.thrift.protocol.TMessage
import org.apache.thrift.protocol.TProtocol

class TMessageToByteEncoder(private val protocol: TProtocol) : MessageToByteEncoder<TMessage>() {
  override fun encode(ctx: ChannelHandlerContext, msg: TMessage, out: ByteBuf) {
    out.writeBytes(protocol.readBinary())
  }
}