// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.Urls.parseEncoded
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.ide.BuiltInServerManager.Companion.getInstance
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.FileResponses.checkCache
import org.jetbrains.io.FileResponses.getContentType
import org.jetbrains.io.send
import java.net.URL
import java.util.*

class PreviewStaticServer : HttpRequestHandler() {
  var resourceProvider: ResourceProvider = ResourceProvider.default

  override fun isSupported(request: FullHttpRequest): Boolean {
    return super.isSupported(request) && request.uri().startsWith(PREFIX)
  }

  override fun process(urlDecoder: QueryStringDecoder,
                       request: FullHttpRequest,
                       context: ChannelHandlerContext): Boolean {
    val path = urlDecoder.path()
    check(path.startsWith(PREFIX)) { "prefix should have been checked by #isSupported" }
    val resourceName = path.substring(PREFIX.length)
    if (resourceProvider.canProvide(resourceName)) {
      sendResource(
        request,
        context.channel(),
        resourceProvider.loadResource(resourceName),
        resourceName
      )
      return true
    }
    return false
  }

  companion object {
    private const val PREFIX = "/4f800f8a-bbed-4dd8-b03c-00449c9f6698/"

    @JvmStatic
    val instance: PreviewStaticServer
      get() = EP_NAME.findExtension(PreviewStaticServer::class.java) ?: error("Could not get server instance!")

    @JvmStatic
    fun createCSP(scripts: List<String>, styles: List<String>): String {
      // We need to remove any query parameters to stop annoying errors in browser console
      fun stripQueryParameters(url: String) = url.replace("?${URL(url).query}", "")
      return """
        default-src 'none';
        script-src ${StringUtil.join(scripts.map(::stripQueryParameters), " ")};
        style-src https: ${StringUtil.join(styles.map(::stripQueryParameters), " ")} 'unsafe-inline';
        img-src file: *; connect-src 'none'; font-src * data: *;
        object-src 'none'; media-src 'none'; child-src 'none';
      """
    }

    @JvmStatic
    fun getStaticUrl(staticPath: String): String {
      val url = parseEncoded("http://localhost:${getInstance().port}$PREFIX$staticPath")
      requireNotNull(url) { "Could not parse url!" }
      return getInstance().addAuthToken(url).toExternalForm()
    }

    private fun sendResource(
      request: HttpRequest,
      channel: Channel,
      resource: ResourceProvider.Resource?,
      resourceName: String
    ) {
      val lastModified = ApplicationInfo.getInstance().buildDate.timeInMillis
      if (checkCache(request, channel, lastModified)) {
        return
      }
      if (resource == null) {
        HttpResponseStatus.NOT_FOUND.send(channel, request)
        return
      }
      val response: FullHttpResponse = DefaultFullHttpResponse(
        HttpVersion.HTTP_1_1,
        HttpResponseStatus.OK,
        Unpooled.wrappedBuffer(resource.content)
      )
      with(response) {
        if (resource.type != null) {
          headers()[HttpHeaderNames.CONTENT_TYPE] = resource.type
        }
        else {
          headers()[HttpHeaderNames.CONTENT_TYPE] = getContentType(resourceName)
        }
        headers()[HttpHeaderNames.CACHE_CONTROL] = "private, must-revalidate"
        headers()[HttpHeaderNames.LAST_MODIFIED] = Date(lastModified)
        send(channel, request)
      }
    }
  }
}
