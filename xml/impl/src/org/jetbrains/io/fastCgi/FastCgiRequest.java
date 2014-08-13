package org.jetbrains.io.fastCgi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.CharsetUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.builtInWebServer.PathInfo;
import org.jetbrains.builtInWebServer.WebServerPathToFileManager;
import org.jetbrains.io.Responses;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Map;

public class FastCgiRequest {
  private static final int PARAMS = 4;
  private static final int BEGIN_REQUEST = 1;
  private static final int RESPONDER = 1;
  private static final int FCGI_KEEP_CONNECTION = 1;
  private static final int STDIN = 5;
  private static final int VERSION = 1;

  private final ByteBuf buffer;
  final int requestId;

  public FastCgiRequest(int requestId, @NotNull ByteBufAllocator allocator) {
    this.requestId = requestId;

    buffer = allocator.buffer();
    writeHeader(buffer, BEGIN_REQUEST, FastCgiConstants.HEADER_LENGTH);
    buffer.writeShort(RESPONDER);
    buffer.writeByte(FCGI_KEEP_CONNECTION);
    buffer.writeZero(5);
  }

  public void writeFileHeaders(@NotNull VirtualFile file, @NotNull Project project, @NotNull CharSequence canonicalRequestPath) {
    PathInfo root = WebServerPathToFileManager.getInstance(project).getRoot(file);
    FastCgiService.LOG.assertTrue(root != null);
    addHeader("DOCUMENT_ROOT", root.getRoot().getPath());
    addHeader("SCRIPT_FILENAME", file.getPath());
    addHeader("SCRIPT_NAME", canonicalRequestPath);
  }

  public final void addHeader(@NotNull String key, @Nullable CharSequence value) {
    if (value == null) {
      return;
    }

    int keyLength = key.length();
    int valLength = value.length();
    writeHeader(buffer, PARAMS, keyLength + valLength + (keyLength < 0x80 ? 1 : 4) + (valLength < 0x80 ? 1 : 4));

    if (keyLength < 0x80) {
      buffer.writeByte(keyLength);
    }
    else {
      buffer.writeByte(0x80 | (keyLength >> 24));
      buffer.writeByte(keyLength >> 16);
      buffer.writeByte(keyLength >> 8);
      buffer.writeByte(keyLength);
    }

    if (valLength < 0x80) {
      buffer.writeByte(valLength);
    }
    else {
      buffer.writeByte(0x80 | (valLength >> 24));
      buffer.writeByte(valLength >> 16);
      buffer.writeByte(valLength >> 8);
      buffer.writeByte(valLength);
    }

    buffer.writeBytes(key.getBytes(CharsetUtil.US_ASCII));
    buffer.writeBytes(Unpooled.copiedBuffer(value, CharsetUtil.UTF_8));
  }

  public void writeHeaders(FullHttpRequest request, Channel clientChannel) {
    addHeader("REQUEST_URI", request.uri());
    addHeader("REQUEST_METHOD", request.method().name());

    InetSocketAddress remote = (InetSocketAddress)clientChannel.remoteAddress();
    addHeader("REMOTE_ADDR", remote.getAddress().getHostAddress());
    addHeader("REMOTE_PORT", Integer.toString(remote.getPort()));

    InetSocketAddress local = (InetSocketAddress)clientChannel.localAddress();
    addHeader("SERVER_SOFTWARE", Responses.getServerHeaderValue());
    addHeader("SERVER_NAME", Responses.getServerHeaderValue());

    addHeader("SERVER_ADDR", local.getAddress().getHostAddress());
    addHeader("SERVER_PORT", Integer.toString(local.getPort()));

    addHeader("GATEWAY_INTERFACE", "CGI/1.1");
    addHeader("SERVER_PROTOCOL", request.protocolVersion().text());
    addHeader("CONTENT_TYPE", request.headers().get(HttpHeaders.Names.CONTENT_TYPE));

    // PHP only, required if PHP was built with --enable-force-cgi-redirect
    addHeader("REDIRECT_STATUS", "200");

    String queryString = "";
    int queryIndex = request.uri().indexOf('?');
    if (queryIndex != -1) {
      queryString = request.uri().substring(queryIndex + 1);
    }
    addHeader("QUERY_STRING", queryString);

    addHeader("CONTENT_LENGTH", String.valueOf(request.content().readableBytes()));

    for (Map.Entry<String, String> entry : request.headers()) {
      addHeader("HTTP_" + entry.getKey().replace('-', '_').toUpperCase(Locale.ENGLISH), entry.getValue());
    }
  }

  final void writeToServerChannel(ByteBuf content, Channel fastCgiChannel) {
    writeHeader(buffer, PARAMS, 0);
    fastCgiChannel.write(buffer);

    if (content.isReadable()) {
      ByteBuf headerBuffer = fastCgiChannel.alloc().buffer(FastCgiConstants.HEADER_LENGTH, FastCgiConstants.HEADER_LENGTH);
      writeHeader(headerBuffer, STDIN, content.readableBytes());
      fastCgiChannel.write(headerBuffer);

      fastCgiChannel.write(content);

      headerBuffer = fastCgiChannel.alloc().buffer(FastCgiConstants.HEADER_LENGTH, FastCgiConstants.HEADER_LENGTH);
      writeHeader(headerBuffer, STDIN, 0);
      fastCgiChannel.write(headerBuffer);
    }
    else {
      content.release();
    }

    fastCgiChannel.flush();
  }

  private void writeHeader(ByteBuf buffer, int type, int length) {
    buffer.writeByte(VERSION);
    buffer.writeByte(type);
    buffer.writeShort(requestId);
    buffer.writeShort(length);
    buffer.writeZero(2);
  }
}