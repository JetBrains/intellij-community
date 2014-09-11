package org.jetbrains.io.fastCgi;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.jetbrains.io.Responses;
import org.jetbrains.io.SimpleChannelInboundHandlerAdapter;

import static org.jetbrains.io.fastCgi.FastCgiService.LOG;

@ChannelHandler.Sharable
public class FastCgiChannelHandler extends SimpleChannelInboundHandlerAdapter<FastCgiResponse> {
  private final ConcurrentIntObjectMap<Channel> requestToChannel;

  public FastCgiChannelHandler(ConcurrentIntObjectMap<Channel> channel) {
    requestToChannel = channel;
  }

  @Override
  protected void messageReceived(ChannelHandlerContext context, FastCgiResponse response) throws Exception {
    ByteBuf buffer = response.getData();
    Channel channel = requestToChannel.remove(response.getId());
    if (channel == null || !channel.isActive()) {
      if (buffer != null) {
        buffer.release();
      }
      return;
    }

    if (buffer == null) {
      Responses.sendStatus(HttpResponseStatus.BAD_GATEWAY, channel);
      return;
    }

    HttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buffer);
    try {
      parseHeaders(httpResponse, buffer);
      Responses.addServer(httpResponse);
      if (!HttpHeaders.isContentLengthSet(httpResponse)) {
        HttpHeaders.setContentLength(httpResponse, buffer.readableBytes());
      }
    }
    catch (Throwable e) {
      buffer.release();
      Responses.sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR, channel);
      LOG.error(e);
    }
    channel.writeAndFlush(httpResponse);
  }

  private static void parseHeaders(HttpResponse response, ByteBuf buffer) {
    StringBuilder builder = new StringBuilder();
    while (buffer.isReadable()) {
      builder.setLength(0);

      String key = null;
      boolean valueExpected = true;
      while (true) {
        int b = buffer.readByte();
        if (b < 0 || b == '\n') {
          break;
        }

        if (b != '\r') {
          if (valueExpected && b == ':') {
            valueExpected = false;

            key = builder.toString();
            builder.setLength(0);
            skipWhitespace(buffer);
          }
          else {
            builder.append((char)b);
          }
        }
      }

      if (builder.length() == 0) {
        // end of headers
        return;
      }

      // skip standard headers
      if (StringUtil.isEmpty(key) || StringUtilRt.startsWithIgnoreCase(key, "http") || StringUtilRt.startsWithIgnoreCase(key, "X-Accel-")) {
        continue;
      }

      String value = builder.toString();
      if (key.equalsIgnoreCase("status")) {
        int index = value.indexOf(' ');
        if (index == -1) {
          LOG.warn("Cannot parse status: " + value);
          response.setStatus(HttpResponseStatus.OK);
        }
        else {
          response.setStatus(HttpResponseStatus.valueOf(Integer.parseInt(value.substring(0, index))));
        }
      }
      else if (!(key.startsWith("http") || key.startsWith("HTTP"))) {
        response.headers().add(key, value);
      }
    }
  }

  private static void skipWhitespace(ByteBuf buffer) {
    while (buffer.isReadable() && buffer.getByte(buffer.readerIndex()) == ' ') {
      buffer.skipBytes(1);
    }
  }
}