// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.intellij.plugins.markdown.settings.MarkdownCssSettings;
import org.intellij.plugins.markdown.ui.preview.javafx.MarkdownJavaFxHtmlPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.BuiltInServerManager;
import org.jetbrains.ide.HttpRequestHandler;
import org.jetbrains.io.FileResponses;
import org.jetbrains.io.Responses;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class PreviewStaticServer extends HttpRequestHandler {
  public static final String INLINE_CSS_FILENAME = "inline.css";
  public static final String COLOR_THEME_CSS_FILENAME = "colors.css";
  private static final Logger LOG = Logger.getInstance(PreviewStaticServer.class);
  private static final String PREFIX = "/4f800f8a-bbed-4dd8-b03c-00449c9f6698/";

  private byte @Nullable [] myInlineStyleBytes = null;
  private long myInlineStyleTimestamp = 0;
  private byte @Nullable [] myColorThemeStylesBytes = null;
  private long myColorThemeStylesTimestamp = 0;

  public static PreviewStaticServer getInstance() {
    return HttpRequestHandler.Companion.getEP_NAME().findExtension(PreviewStaticServer.class);
  }

  @NotNull
  public static String createCSP(@NotNull List<String> scripts, @NotNull List<String> styles) {
    return "default-src 'none'; script-src " + StringUtil.join(scripts, " ") + "; "
           + "style-src https: " + StringUtil.join(styles, " ") + "; "
           + "img-src file: *; connect-src 'none'; font-src *; " +
           "object-src 'none'; media-src 'none'; child-src 'none';";
  }

  @NotNull
  private static String getStaticUrl(@NotNull String staticPath) {
    Url url = Urls.parseEncoded("http://localhost:" + BuiltInServerManager.getInstance().getPort() + PREFIX + staticPath);
    return BuiltInServerManager.getInstance().addAuthToken(Objects.requireNonNull(url)).toExternalForm();
  }

  @NotNull
  public static String getScriptUrl(@NotNull String scriptFileName) {
    return getStaticUrl("scripts/" + scriptFileName);
  }

  @NotNull
  public static String getStyleUrl(@NotNull String scriptFileName) {
    return getStaticUrl("styles/" + scriptFileName);
  }

  public void setInlineStyle(@Nullable String inlineStyle) {
    myInlineStyleBytes = inlineStyle == null ? null : inlineStyle.getBytes(StandardCharsets.UTF_8);
    myInlineStyleTimestamp = System.currentTimeMillis();
  }

  public void setColorThemeStyles(@Nullable String overrides) {
    myColorThemeStylesBytes = overrides == null ? null : overrides.getBytes(StandardCharsets.UTF_8);
    myColorThemeStylesTimestamp = System.currentTimeMillis();
  }

  @Override
  public boolean isSupported(@NotNull FullHttpRequest request) {
    return super.isSupported(request) && request.uri().startsWith(PREFIX);
  }

  @Override
  public boolean process(@NotNull QueryStringDecoder urlDecoder,
                         @NotNull FullHttpRequest request,
                         @NotNull ChannelHandlerContext context) {
    final String path = urlDecoder.path();
    if (!path.startsWith(PREFIX)) {
      throw new IllegalStateException("prefix should have been checked by #isSupported");
    }

    final String payLoad = path.substring(PREFIX.length());
    final List<String> typeAndName = StringUtil.split(payLoad, "/");

    if (typeAndName.size() != 2) {
      return false;
    }
    final String contentType = typeAndName.get(0);
    final String fileName = typeAndName.get(1);

    if ("scripts".equals(contentType) && MarkdownHtmlPanel.SCRIPTS.contains(fileName)) {
      sendResource(request,
                   context.channel(),
                   MarkdownJavaFxHtmlPanel.class,
                   fileName);
    }
    else if ("styles".equals(contentType) && MarkdownHtmlPanel.STYLES.contains(fileName)) {
      if (INLINE_CSS_FILENAME.equals(fileName)) {
        sendStyleFromMemory(myInlineStyleBytes, myInlineStyleTimestamp, request, context.channel());
      }
      else if (COLOR_THEME_CSS_FILENAME.equals(fileName)) {
        sendStyleFromMemory(myColorThemeStylesBytes, myColorThemeStylesTimestamp, request, context.channel());
      }
      else {
        sendResource(request,
                     context.channel(),
                     MarkdownCssSettings.class,
                     fileName);
      }
    }
    else {
      return false;
    }

    return true;
  }

  private static void sendStyleFromMemory(byte @Nullable []  content,
                                          long timestamp,
                                          @NotNull HttpRequest request,
                                          @NotNull Channel channel) {
    if (FileResponses.INSTANCE.checkCache(request, channel, timestamp)) {
      return;
    }

    if (content == null) {
      Responses.send(HttpResponseStatus.NOT_FOUND, channel, request);
      return;
    }

    FullHttpResponse response =
      new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(content));
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/css");
    response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, must-revalidate");
    response.headers().set(HttpHeaderNames.LAST_MODIFIED, new Date(timestamp));
    Responses.send(response, channel, request);
  }

  private static void sendResource(@NotNull HttpRequest request,
                                   @NotNull Channel channel,
                                   @NotNull Class<?> clazz,
                                   @NotNull String resourceName) {
    long lastModified = ApplicationInfo.getInstance().getBuildDate().getTimeInMillis();
    if (FileResponses.INSTANCE.checkCache(request, channel, lastModified)) {
      return;
    }

    byte[] data;
    try (final InputStream inputStream = clazz.getResourceAsStream(resourceName)) {
      if (inputStream == null) {
        Responses.send(HttpResponseStatus.NOT_FOUND, channel, request);
        return;
      }

      data = FileUtilRt.loadBytes(inputStream);
    }
    catch (IOException e) {
      LOG.warn(e);
      Responses.send(HttpResponseStatus.INTERNAL_SERVER_ERROR, channel, request);
      return;
    }

    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(data));
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, FileResponses.INSTANCE.getContentType(resourceName));
    response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, must-revalidate");
    response.headers().set(HttpHeaderNames.LAST_MODIFIED, new Date(lastModified));
    Responses.send(response, channel, request);
  }
}
