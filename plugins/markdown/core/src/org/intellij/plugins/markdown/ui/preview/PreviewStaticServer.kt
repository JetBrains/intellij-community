// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.util.Urls
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.FileResponses.checkCache
import org.jetbrains.io.FileResponses.getContentType
import org.jetbrains.io.send
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

  private fun getProviderHash(path: String): Int? = path.split('/').getOrNull(2)?.toIntOrNull()

  private fun getStaticPath(path: String): String = path.split('/').drop(3).joinToString(separator = "/")

  private fun obtainResourceProvider(path: String): ResourceProvider? {
    val providerHash = getProviderHash(path) ?: return null
    return synchronized(resourceProviders) { resourceProviders.getOrDefault(providerHash, null) }
  }

  override fun isSupported(request: FullHttpRequest): Boolean =
    super.isSupported(request) && request.uri().startsWith(ENDPOINT_PREFIX_PATH)

  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
    val path = urlDecoder.path()
    check(path.startsWith(ENDPOINT_PREFIX_PATH)) { "prefix should have been checked by #isSupported" }
    val resourceProvider = obtainResourceProvider(path) ?: return false
    val resourceName = getStaticPath(path)
    if (resourceProvider.canProvide(resourceName)) {
      sendResource(request, context.channel(), resourceProvider.loadResource(resourceName), resourceName)
      return true
    }
    return false
  }

  @Suppress("CompanionObjectInExtension")
  companion object {
    private const val ENDPOINT_PREFIX = "markdownPreview"
    private const val ENDPOINT_PREFIX_PATH = "/${ENDPOINT_PREFIX}"

    @JvmStatic
    val instance: PreviewStaticServer
      get() = EP_NAME.findExtension(PreviewStaticServer::class.java) ?: error("Could not get server instance!")

    @JvmStatic
    internal fun createCSP(scripts: List<String>, styles: List<String>): String = """
      default-src 'none';
      script-src ${scripts.joinToString(" ")};
      style-src https: ${styles.joinToString(" ")} 'unsafe-inline';
      img-src file: * data:; connect-src 'none'; font-src * data: *;
      object-src 'none'; media-src 'none'; child-src 'none';
    """

    /**
     * Expected to return same URL on each call for same [resourceProvider] and [staticPath],
     * if [resourceProvider] was not unregistered between those calls.
     */
    @JvmStatic
    fun getStaticUrl(resourceProvider: ResourceProvider, staticPath: String): String {
      val providerHash = resourceProvider.hashCode()
      val port = BuiltInServerManager.getInstance().port
      val raw = "http://localhost:${port}/${ENDPOINT_PREFIX}/${providerHash}/${staticPath}"
      requireNotNull(Urls.parseEncoded(raw)) { "Invalid URL: ${raw}" }
      return raw
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
      return if (type in typesForExplicitUtfCharset) "${type}; charset=utf-8" else type
    }

    private fun sendResource(request: HttpRequest, channel: Channel, resource: ResourceProvider.Resource?, resourceName: String) {
      val lastModified = ApplicationInfo.getInstance().buildDate.timeInMillis
      if (checkCache(request, channel, lastModified)) {
        return
      }
      if (resource == null) {
        HttpResponseStatus.NOT_FOUND.send(channel, request)
        return
      }
      val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(resource.content))
      with(response) {
        headers()[HttpHeaderNames.CONTENT_TYPE] = when (val type = resource.type) {
          null -> guessContentType(resourceName)
          else -> type
        }
        headers()[HttpHeaderNames.CACHE_CONTROL] = "no-cache"
        headers()[HttpHeaderNames.LAST_MODIFIED] = Date(lastModified)
        send(channel, request)
      }
    }
  }
}
