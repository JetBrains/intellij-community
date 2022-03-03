// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.Disposable
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

/**
 * Will serve resources provided by registered resource providers.
 */
class PreviewStaticServer : HttpRequestHandler() {
  private val defaultResourceProvider = ResourceProvider.DefaultResourceProvider()
  private val resourceProviders = hashMapOf<Int, ResourceProvider>(defaultResourceProvider.hashCode() to defaultResourceProvider)

  /**
   * @return [Disposable] which will unregister [resourceProvider].
   */
  @Synchronized
  fun registerResourceProvider(resourceProvider: ResourceProvider): Disposable {
    resourceProviders[resourceProvider.hashCode()] = resourceProvider
    return Disposable { unregisterResourceProvider(resourceProvider) }
  }

  /**
   * Prefer unregistering providers by disposing [Disposable] returned by [registerResourceProvider].
   */
  @Synchronized
  fun unregisterResourceProvider(resourceProvider: ResourceProvider) {
    resourceProviders.remove(resourceProvider.hashCode())
  }

  private fun getProviderHash(path: String): Int? {
    return path.split('/').getOrNull(2)?.toIntOrNull()
  }

  private fun getStaticPath(path: String): String {
    return path.split('/').drop(3).joinToString(separator = "/")
  }

  private fun obtainResourceProvider(path: String): ResourceProvider? {
    val providerHash = getProviderHash(path) ?: return null
    return synchronized(resourceProviders) { resourceProviders.getOrDefault(providerHash, null) }
  }

  override fun isSupported(request: FullHttpRequest): Boolean {
    if (!super.isSupported(request)) {
      return false
    }
    val path = request.uri()
    return path.startsWith(prefixPath)
  }

  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
    val path = urlDecoder.path()
    check(path.startsWith(prefixPath)) { "prefix should have been checked by #isSupported" }
    val resourceProvider = obtainResourceProvider(path) ?: return false
    val resourceName = getStaticPath(path)
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
    private const val prefixUuid = "4f800f8a-bbed-4dd8-b03c-00449c9f6698"
    private const val prefixPath = "/$prefixUuid"

    @JvmStatic
    val instance: PreviewStaticServer
      get() = EP_NAME.findExtension(PreviewStaticServer::class.java) ?: error("Could not get server instance!")

    @JvmStatic
    fun createCSP(scripts: List<String>, styles: List<String>): String {
      // We need to remove any query parameters to stop annoying errors in the browser console
      fun stripQueryParameters(url: String) = url.replace("?${URL(url).query}", "")
      return """
        default-src 'none';
        script-src ${StringUtil.join(scripts.map(::stripQueryParameters), " ")};
        style-src https: ${StringUtil.join(styles.map(::stripQueryParameters), " ")} 'unsafe-inline';
        img-src file: *; connect-src 'none'; font-src * data: *;
        object-src 'none'; media-src 'none'; child-src 'none';
      """
    }

    /**
     * Expected to return same URL on each call for same [resourceProvider] and [staticPath],
     * if [resourceProvider] was not unregistered between those calls.
     */
    @JvmStatic
    fun getStaticUrl(resourceProvider: ResourceProvider, staticPath: String): String {
      val providerHash = resourceProvider.hashCode()
      val port = getInstance().port
      val raw = "http://localhost:$port/$prefixUuid/$providerHash/$staticPath"
      val url = parseEncoded(raw)
      requireNotNull(url) { "Could not parse url!" }
      return getInstance().addAuthToken(url).toExternalForm()
    }

    @Deprecated("Use PreviewStaticServer.getStaticUrl(ResourceProvider, String) instead")
    @JvmStatic
    fun getStaticUrl(staticPath: String): String {
      return getStaticUrl(instance.defaultResourceProvider, staticPath)
    }

    /**
     * The types for which `";charset=utf-8"` will be appended (only if guessed by [guessContentType]).
     */
    private val typesForExplicitUtfCharset = arrayOf(
      "application/javascript",
      "text/html",
      "text/css",
      "image/svg+xml"
    )

    private fun guessContentType(resourceName: String): String {
      val type = getContentType(resourceName)
      return if (type in typesForExplicitUtfCharset) {
        "$type; charset=utf-8"
      } else type
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
          headers()[HttpHeaderNames.CONTENT_TYPE] = guessContentType(resourceName)
        }
        headers()[HttpHeaderNames.CACHE_CONTROL] = "private, must-revalidate"
        headers()[HttpHeaderNames.LAST_MODIFIED] = Date(lastModified)
        send(channel, request)
      }
    }
  }
}
