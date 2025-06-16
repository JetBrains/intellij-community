// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.webservice

import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.performancePlugin.remotedriver.webservice.routing.CantFindRouteException
import com.jetbrains.performancePlugin.remotedriver.webservice.routing.Routing
import com.jetbrains.performancePlugin.remotedriver.webservice.routing.StaticFile
import com.jetbrains.performancePlugin.remotedriver.webservice.routing.route
import com.jetbrains.performancePlugin.remotedriver.xpath.XpathDataModelCreator
import com.jetbrains.performancePlugin.remotedriver.xpath.convertToHtml
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.ide.RestService
import org.jetbrains.io.response

internal class UiHierarchyWebService : RestService() {
  init {
    if (!Registry.`is`("expose.ui.hierarchy.url")) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun getServiceName(): String {
    return "remote-driver"
  }

  override fun isMethodSupported(method: HttpMethod): Boolean {
    return method in listOf(HttpMethod.GET, HttpMethod.POST)
  }

  private val routing: Routing = route("/${PREFIX}/${getServiceName()}") {
    get("/") {
      hierarchy()
    }
    static("/static")
  }

  private fun hierarchy(): String {
    val doc = XpathDataModelCreator().create(null)
    return doc.convertToHtml()
  }

  override fun getMaxRequestsPerMinute(): Int = Int.MAX_VALUE

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    try {
      val response = when (val result = routing.handleRequest(urlDecoder, request, context)) {
        is String -> response("text/html", Unpooled.wrappedBuffer(result.toByteArray()))
        is StaticFile -> response(result.type, Unpooled.wrappedBuffer(result.byteArray))
        else -> throw NotImplementedError("${result::class.java} type is not supported")
      }
      sendResponse(request, context, response)
    }
    catch (e: CantFindRouteException) {
      e.printStackTrace()
      sendStatus(HttpResponseStatus.BAD_REQUEST, HttpUtil.isKeepAlive(request), context.channel())
    }
    return null
  }
}
